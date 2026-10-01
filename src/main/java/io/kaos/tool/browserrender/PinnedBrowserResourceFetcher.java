package io.kaos.tool.browserrender;

import io.kaos.tool.httpget.HttpGetException;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetTarget;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** Fetches one browser resource through KAOS-owned DNS validation and address pinning. */
public final class PinnedBrowserResourceFetcher {
    private static final int MAX_HEADERS = 32;
    private static final int MAX_LINE = 4096;

    private PinnedBrowserResourceFetcher() { }

    public static BrowserResource fetch(URI uri, Duration timeout, int maxBytes) {
        HttpGetPermissionValidator validator = new HttpGetPermissionValidator(Set.of(uri.getHost()));
        HttpGetTarget target;
        try {
            target = validator.validate(new HttpGetRequest(uri.toASCIIString()));
        } catch (HttpGetException exception) {
            throw map(exception);
        }
        return execute(validator::resolvePublicDestinationForConnection, target, timeout, maxBytes);
    }

    static BrowserResource execute(Function<HttpGetTarget, InetAddress> resolver,
            HttpGetTarget target, Duration timeout, int maxBytes) {
        if (maxBytes < 1 || timeout.isZero() || timeout.isNegative()) {
            throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
        }
        var request = new HttpGet(target.uri());
        request.setHeader("Accept", "text/html,application/xhtml+xml,text/css,application/javascript,text/javascript");
        request.setHeader("User-Agent", "KAOS-Research-Renderer/1.0");
        var manager = new PoolingHttpClientConnectionManagerBuilder() {
            @Override protected org.apache.hc.client5.http.io.HttpClientConnectionOperator createConnectionOperator(
                    org.apache.hc.client5.http.SchemePortResolver ports, DnsResolver dns,
                    org.apache.hc.client5.http.ssl.TlsSocketStrategy tls) {
                return new org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator(
                        ignored -> new java.net.Socket(java.net.Proxy.NO_PROXY), ports, dns,
                        org.apache.hc.core5.http.config.RegistryBuilder
                                .<org.apache.hc.client5.http.ssl.TlsSocketStrategy>create()
                                .register("https", tls).build());
            }
        }
                .setDnsResolver(new DnsResolver() {
                    public InetAddress[] resolve(String host) throws UnknownHostException {
                        if (!host.equals(target.uri().getHost()) || request.isCancelled()) {
                            throw new UnknownHostException("Request stopped.");
                        }
                        InetAddress address = resolver.apply(target);
                        if (Thread.currentThread().isInterrupted() || request.isCancelled()) {
                            throw new UnknownHostException("Request stopped.");
                        }
                        return new InetAddress[] {address};
                    }
                    public String resolveCanonicalHostname(String host) { return host; }
                })
                .setConnectionFactory(org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory.builder()
                        .http1Config(org.apache.hc.core5.http.config.Http1Config.custom()
                                .setMaxLineLength(MAX_LINE).setMaxHeaderCount(MAX_HEADERS).build()).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(2))
                        .setSocketTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build())
                .build();
        var client = HttpClients.custom().setConnectionManager(manager)
                .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
                .disableContentCompression().disableAuthCaching()
                .setDefaultRequestConfig(RequestConfig.custom().setAuthenticationEnabled(false)
                        .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build())
                .build();
        var worker = Executors.newVirtualThreadPerTaskExecutor();
        var pending = worker.submit(() -> client.execute(request, response -> {
            if (request.isCancelled()) throw new BrowserRenderException(BrowserRenderException.Reason.INTERRUPTED);
            int status = response.getCode();
            String type = header(response, "Content-Type").orElse("");
            String location = header(response, "Location").orElse("");
            String encoding = header(response, "Content-Encoding").orElse("identity");
            if (!"identity".equalsIgnoreCase(encoding)) {
                throw new BrowserRenderException(BrowserRenderException.Reason.UNSUPPORTED_CONTENT);
            }
            long declared = parseLength(header(response, "Content-Length"));
            if (declared > maxBytes) throw new BrowserRenderException(BrowserRenderException.Reason.TOO_LARGE);
            byte[] body = status < 200 || status >= 300 ? new byte[0]
                    : readBounded(response.getEntity() == null
                            ? java.io.InputStream.nullInputStream() : response.getEntity().getContent(), maxBytes);
            return new BrowserResource(target.uri(), status, type, location, body);
        }));
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BrowserRenderException(BrowserRenderException.Reason.INTERRUPTED);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof BrowserRenderException failure) throw failure;
            if (cause instanceof HttpGetException failure) throw map(failure);
            if (cause instanceof java.net.SocketTimeoutException) {
                throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
            }
            throw new BrowserRenderException(BrowserRenderException.Reason.UNAVAILABLE);
        } finally {
            request.cancel();
            pending.cancel(true);
            client.close(CloseMode.IMMEDIATE);
            worker.shutdownNow();
        }
    }

    private static Optional<String> header(org.apache.hc.core5.http.ClassicHttpResponse response, String name) {
        var value = response.getFirstHeader(name);
        return value == null ? Optional.empty() : Optional.of(value.getValue());
    }

    private static long parseLength(Optional<String> raw) {
        if (raw.isEmpty()) return -1;
        try {
            long value = Long.parseLong(raw.get());
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new BrowserRenderException(BrowserRenderException.Reason.UNSUPPORTED_CONTENT);
        }
    }

    private static byte[] readBounded(java.io.InputStream input, int maxBytes) throws IOException {
        try (input; var output = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new BrowserRenderException(BrowserRenderException.Reason.INTERRUPTED);
                }
                if (read > maxBytes - total) {
                    throw new BrowserRenderException(BrowserRenderException.Reason.TOO_LARGE);
                }
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toByteArray();
        }
    }

    private static BrowserRenderException map(HttpGetException failure) {
        return new BrowserRenderException(switch (failure.reason()) {
            case INVALID_REQUEST, DISALLOWED_HOST, INVALID_CONFIGURATION -> BrowserRenderException.Reason.INVALID_REQUEST;
            case NON_PUBLIC_DESTINATION -> BrowserRenderException.Reason.NON_PUBLIC_DESTINATION;
            case TIMEOUT -> BrowserRenderException.Reason.TIMEOUT;
            case INTERRUPTED -> BrowserRenderException.Reason.INTERRUPTED;
            default -> BrowserRenderException.Reason.UNAVAILABLE;
        });
    }
}
