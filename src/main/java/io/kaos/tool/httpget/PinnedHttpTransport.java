package io.kaos.tool.httpget;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

/** One foreground-owned attempt; the connection uses only a validated DNS address. */
final class PinnedHttpTransport {
    private PinnedHttpTransport() { }

    static HttpGetResult execute(HttpGetPermissionValidator validator, HttpGetTarget target,
            Duration timeout) {
        return executeWithRetry(validator::resolvePublicDestination, target, timeout);
    }

    // Package-only seam for controlled loopback fixtures; no runtime configuration can select it.
    static HttpGetResult execute(java.util.function.Function<HttpGetTarget, InetAddress> resolver,
            HttpGetTarget target, Duration timeout) {
        return executeWithRetry(resolver, target, timeout);
    }

    private static HttpGetResult executeWithRetry(
            java.util.function.Function<HttpGetTarget, InetAddress> resolver,
            HttpGetTarget target, Duration timeout) {
        HttpGetException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return executeOnce(resolver, target, timeout);
            } catch (HttpGetException exception) {
                last = exception;
                if (exception.reason() != HttpGetException.Reason.UNAVAILABLE
                        && exception.reason() != HttpGetException.Reason.TIMEOUT) throw exception;
                if (attempt < 2) {
                    try { Thread.sleep(100L * (attempt + 1)); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new HttpGetException(HttpGetException.Reason.INTERRUPTED);
                    }
                }
            }
        }
        throw last;
    }

    private static HttpGetResult executeOnce(
            java.util.function.Function<HttpGetTarget, InetAddress> resolver,
            HttpGetTarget target, Duration timeout) {
        var request = new HttpGet(target.uri());
        request.setHeader("Accept", "text/plain, text/html, application/json, application/xml");
        var manager = new PoolingHttpClientConnectionManagerBuilder() {
            @Override protected org.apache.hc.client5.http.io.HttpClientConnectionOperator createConnectionOperator(
                    org.apache.hc.client5.http.SchemePortResolver ports, DnsResolver dns,
                    org.apache.hc.client5.http.ssl.TlsSocketStrategy tls) {
                // A default Java Socket can consult the global SOCKS ProxySelector even with a direct HTTP route.
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
                        if (Thread.currentThread().isInterrupted()) {
                            throw new UnknownHostException("Request stopped.");
                        }
                        if (request.isCancelled()) throw new UnknownHostException("Request stopped.");
                        return new InetAddress[] {address};
                    }
                    public String resolveCanonicalHostname(String host) { return host; }
                })
                .setConnectionFactory(org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory.builder()
                        .http1Config(org.apache.hc.core5.http.config.Http1Config.custom()
                                .setMaxLineLength(4096).setMaxHeaderCount(32).build()).build())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(2))
                        .setSocketTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build())
                .build();
        // No system properties, system proxy, cookies, credential provider, decompression or retry.
        var client = HttpClients.custom().setConnectionManager(manager)
                .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
                .disableContentCompression().disableAuthCaching()
                .setDefaultRequestConfig(RequestConfig.custom().setAuthenticationEnabled(false)
                        .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build())
                .build();
        var worker = Executors.newVirtualThreadPerTaskExecutor();
        var pending = worker.submit(() -> client.execute(request, response -> {
            if (request.isCancelled()) throw new HttpGetException(HttpGetException.Reason.INTERRUPTED);
            var entity = response.getEntity();
            try {
                return HttpGetExecutor.consume(target, response.getCode(), name -> {
                    var header = response.getFirstHeader(name);
                    return header == null ? Optional.empty() : Optional.of(header.getValue());
                }, entity == null ? java.io.InputStream.nullInputStream() : entity.getContent());
            } finally {
                // Abort instead of draining a rejected/oversized response for connection reuse.
                request.cancel();
            }
        }));
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new HttpGetException(HttpGetException.Reason.TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpGetException(HttpGetException.Reason.INTERRUPTED);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof HttpGetException failure) throw failure;
            if (cause instanceof java.net.SocketTimeoutException) {
                throw new HttpGetException(HttpGetException.Reason.TIMEOUT);
            }
            String detail = cause instanceof java.net.UnknownHostException ? "DNS_FAILURE"
                    : cause instanceof javax.net.ssl.SSLException ? "TLS_FAILURE"
                    : cause instanceof java.net.ConnectException ? "CONNECTIVITY_FAILURE"
                    : "NETWORK_FAILURE";
            throw new HttpGetException(HttpGetException.Reason.UNAVAILABLE, 0, "", target.uri().toASCIIString(),
                    java.util.Map.of(), detail);
        } finally {
            request.cancel();
            pending.cancel(true);
            client.close(CloseMode.IMMEDIATE);
            worker.shutdownNow();
        }
    }
}
