package io.kaos.tool.websearch;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One approved request to configured infrastructure; never follows a result URL. */
public final class SearxngClient {
    public static final String URL_ENVIRONMENT_VARIABLE = "KAOS_WEB_SEARCH_SEARXNG_URL";
    public static final String URL_SYSTEM_PROPERTY = "kaos.web-search.searxng-url";
    public static final int MAX_RESPONSE_BYTES = 65_536;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxStringLength(MAX_RESPONSE_BYTES).maxNameLength(256).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final URI endpoint;
    private final Duration timeout;
    public SearxngClient(String configuredUrl) { this(configuredUrl, Duration.ofSeconds(15)); }
    SearxngClient(String configuredUrl, Duration timeout) {
        if (configuredUrl == null || configuredUrl.isBlank()) {
            throw failure(WebSearchException.Reason.SEARCH_SERVICE_NOT_CONFIGURED);
        }
        try {
            URI base = URI.create(configuredUrl);
            if (!List.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                    || base.getRawUserInfo() != null || base.getRawQuery() != null
                    || base.getRawFragment() != null || base.getPort() == 0 || base.getPort() > 65535
                    || !(base.getRawPath().isEmpty() || "/".equals(base.getRawPath()))) {
                throw new IllegalArgumentException();
            }
            endpoint = base.resolve("/search");
        } catch (IllegalArgumentException exception) {
            throw failure(WebSearchException.Reason.INVALID_CONFIGURATION);
        }
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout");
        this.timeout = timeout;
    }
    public static SearxngClient load() {
        try {
            String property = System.getProperty(URL_SYSTEM_PROPERTY);
            return new SearxngClient(property != null ? property : System.getenv(URL_ENVIRONMENT_VARIABLE));
        } catch (SecurityException exception) {
            throw failure(WebSearchException.Reason.INVALID_CONFIGURATION);
        }
    }
    public WebSearchResult execute(WebSearchApproval.Grant grant) {
        WebSearchRequest approved = grant.claim();
        if (Thread.currentThread().isInterrupted()) throw failure(WebSearchException.Reason.CANCELLED);
        URI uri = URI.create(endpoint.toASCIIString() + "?q="
                + URLEncoder.encode(approved.query(), StandardCharsets.UTF_8) + "&format=json");
        // No proxy, authenticator, cookie handler, redirects, or retries.
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).proxy(new ProxySelector() {
                    public List<Proxy> select(URI ignored) { return List.of(Proxy.NO_PROXY); }
                    public void connectFailed(URI ignored, SocketAddress address, java.io.IOException error) { }
                }).build();
        LimitedBody body = new LimitedBody();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            pending = client.sendAsync(HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("Accept", "application/json").GET().build(), info -> body);
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type")
                    .orElse("").split(";", 2)[0].strip().equalsIgnoreCase("application/json")) {
                throw failure(WebSearchException.Reason.INVALID_RESPONSE);
            }
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(response.body())).toString();
            return normalize(approved, JSON.readTree(text));
        } catch (WebSearchException exception) {
            throw exception;
        } catch (TimeoutException exception) {
            throw failure(WebSearchException.Reason.SEARCH_TIMEOUT);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(WebSearchException.Reason.CANCELLED);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof WebSearchException classified) throw classified;
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw failure(WebSearchException.Reason.SEARCH_TIMEOUT);
            }
            throw failure(WebSearchException.Reason.SEARCH_SERVICE_UNAVAILABLE);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw failure(WebSearchException.Reason.INVALID_RESPONSE);
        } finally {
            body.cancel();
            if (pending != null) pending.cancel(true);
            client.shutdownNow();
        }
    }
    private static WebSearchResult normalize(WebSearchRequest request, JsonNode root) {
        if (root == null || !root.isObject() || !root.path("results").isArray()) {
            throw failure(WebSearchException.Reason.INVALID_RESPONSE);
        }
        var entries = new ArrayList<WebSearchResult.Entry>();
        // SearXNG has no portable result-count parameter: retain the first five in provider order.
        for (JsonNode node : root.get("results")) {
            if (entries.size() == WebSearchResult.MAX_RESULTS) break;
            if (!node.isObject() || !node.path("title").isTextual()
                    || !node.path("url").isTextual()
                    || (node.has("content") && !node.get("content").isTextual())) {
                throw failure(WebSearchException.Reason.INVALID_RESPONSE);
            }
            try {
                entries.add(new WebSearchResult.Entry(node.get("title").textValue(),
                        node.get("url").textValue(), node.path("content").asText("")));
            } catch (WebSearchException exception) {
                throw failure(WebSearchException.Reason.INVALID_RESPONSE);
            }
        }
        var result = new WebSearchResult(request, entries);
        WebSearchToolContract.encodeResult(result); // Enforce total normalized payload boundary now.
        return result;
    }
    private static WebSearchException failure(WebSearchException.Reason reason) {
        return new WebSearchException(reason);
    }
    @Override public String toString() { return "SearxngClient[endpoint=REDACTED]"; }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || result.isDone()) { value.cancel(); return; }
            subscription = value;
            value.request(1);
        }
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    result.completeExceptionally(failure(WebSearchException.Reason.RESULT_TOO_LARGE));
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable ignored) {
            result.completeExceptionally(failure(WebSearchException.Reason.SEARCH_SERVICE_UNAVAILABLE));
        }
        public synchronized void onComplete() { result.complete(bytes.toByteArray()); }
        synchronized void cancel() {
            result.cancel(false);
            if (subscription != null) subscription.cancel();
        }
    }
}
