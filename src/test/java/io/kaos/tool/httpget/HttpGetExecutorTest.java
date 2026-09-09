package io.kaos.tool.httpget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class HttpGetExecutorTest {
    @Test
    void performsOneBoundedGetAndReturnsStrictText() {
        StubHttpClient client = new StubHttpClient(200, "text/plain; charset=utf-8",
                "retrieved text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HttpGetResult result = executor(client).execute(approved());

        assertEquals("retrieved text", result.content());
        assertEquals("GET", client.request.method());
        assertEquals(URI.create("https://example.com/reference"), client.request.uri());
    }

    @Test
    void rejectsRedirectBinaryOversizeAndMalformedUtf8() {
        assertReason(HttpGetException.Reason.REDIRECTED,
                () -> executor(new StubHttpClient(302, "text/plain", new byte[0]))
                        .execute(approved()));
        assertReason(HttpGetException.Reason.UNSUPPORTED_MEDIA_TYPE,
                () -> executor(new StubHttpClient(200, "application/octet-stream", new byte[] {1}))
                        .execute(approved()));
        assertReason(HttpGetException.Reason.TOO_LARGE,
                () -> executor(new StubHttpClient(200, "text/plain",
                        new byte[HttpGetResult.MAX_CONTENT_UTF8_BYTES + 1]))
                        .execute(approved()));
        assertReason(HttpGetException.Reason.INVALID_UTF8,
                () -> executor(new StubHttpClient(200, "text/plain",
                        new byte[] {(byte) 0xc3, 0x28})).execute(approved()));
    }

    private static HttpGetExecutor executor(HttpClient client) {
        HttpGetPermissionValidator validator = new HttpGetPermissionValidator(
                Set.of("example.com"), ignored -> {
                    try {
                        return new InetAddress[] {InetAddress.getByAddress(
                                new byte[] {93, (byte) 184, (byte) 216, 34})};
                    } catch (java.net.UnknownHostException exception) {
                        throw new AssertionError(exception);
                    }
                });
        return new HttpGetExecutor(validator, client, Duration.ofSeconds(1));
    }

    private static HttpGetApproval.Grant approved() {
        HttpGetTarget target = new HttpGetTarget(
                new HttpGetRequest("https://example.com/reference"),
                URI.create("https://example.com/reference"));
        return new HttpGetApproval(target).decide("approve").grant().orElseThrow();
    }

    private static void assertReason(
            HttpGetException.Reason reason, org.junit.jupiter.api.function.Executable action) {
        assertEquals(reason, assertThrows(HttpGetException.class, action).reason());
    }

    private static final class StubHttpClient extends HttpClient {
        private final int status;
        private final String contentType;
        private final byte[] body;
        private HttpRequest request;

        private StubHttpClient(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body.clone();
        }

        @Override
        public <T> HttpResponse<T> send(
                HttpRequest sent, HttpResponse.BodyHandler<T> handler) throws IOException {
            request = sent;
            @SuppressWarnings("unchecked")
            T typedBody = (T) new ByteArrayInputStream(body);
            return new StubResponse<>(sent, status, contentType, body.length, typedBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException exception) {
                throw new AssertionError(exception);
            }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_2; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }

    private record StubResponse<T>(HttpRequest request, int statusCode,
            String contentType, int length, T body) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(
                    "Content-Type", List.of(contentType),
                    "Content-Length", List.of(Integer.toString(length))),
                    (ignoredName, ignoredValue) -> true);
        }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }
}
