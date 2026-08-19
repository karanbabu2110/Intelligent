package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class OllamaConnectivityTest {
    @Test
    void reportsTheSafeVersionFromAValidLocalResponse() throws Exception {
        try (LocalVersionServer server = LocalVersionServer.responding(
                200, "{\"version\":\"0.11.4\"}", Duration.ZERO)) {
            OllamaConnectivity.Result result = connectivity(server.endpoint()).check();

            assertTrue(result.reachable());
            assertEquals(OllamaConnectivity.Status.REACHABLE, result.status());
            assertEquals("0.11.4", result.version());
        }
    }

    @Test
    void treatsANonSuccessResponseAsUnavailableWithoutReturningItsBody() throws Exception {
        String privateResponse = "private-provider-detail";
        try (LocalVersionServer server =
                LocalVersionServer.responding(503, privateResponse, Duration.ZERO)) {
            OllamaConnectivity.Result result = connectivity(server.endpoint()).check();

            assertEquals(OllamaConnectivity.Status.UNAVAILABLE, result.status());
            assertFalse(result.reachable());
            assertEquals("", result.version());
            assertFalse(result.toString().contains(privateResponse));
        }
    }

    @Test
    void rejectsAMalformedVersionResponseWithoutReturningItsBody() throws Exception {
        String privateResponse = "{\"unexpected\":\"private-provider-detail\"}";
        try (LocalVersionServer server =
                LocalVersionServer.responding(200, privateResponse, Duration.ZERO)) {
            OllamaConnectivity.Result result = connectivity(server.endpoint()).check();

            assertEquals(OllamaConnectivity.Status.INVALID_RESPONSE, result.status());
            assertFalse(result.reachable());
            assertEquals("", result.version());
            assertFalse(result.toString().contains(privateResponse));
        }
    }

    @Test
    void returnsWhenTheLocalDependencyIsUnavailable() throws Exception {
        URI unavailableEndpoint;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailableEndpoint = URI.create(
                    "http://127.0.0.1:" + socket.getLocalPort() + "/api/version");
        }

        OllamaConnectivity.Result result = connectivity(unavailableEndpoint).check();

        assertEquals(OllamaConnectivity.Status.UNAVAILABLE, result.status());
        assertFalse(result.reachable());
        assertEquals("", result.version());
    }

    @Test
    void boundsAResponseThatTakesTooLong() throws Exception {
        try (LocalVersionServer server = LocalVersionServer.responding(
                200, "{\"version\":\"late-private-version\"}", Duration.ofSeconds(1))) {
            OllamaConnectivity.Result result = connectivity(
                            server.endpoint(), Duration.ofMillis(50))
                    .check();

            assertEquals(OllamaConnectivity.Status.UNAVAILABLE, result.status());
            assertFalse(result.reachable());
            assertEquals("", result.version());
        }
    }

    @Test
    void preservesInterruptionWithoutReturningFailureDetails() throws Exception {
        try (LocalVersionServer server = LocalVersionServer.responding(
                200, "{\"version\":\"private-version\"}", Duration.ofSeconds(1))) {
            Thread.currentThread().interrupt();
            try {
                OllamaConnectivity.Result result = connectivity(server.endpoint()).check();

                assertEquals(OllamaConnectivity.Status.INTERRUPTED, result.status());
                assertFalse(result.reachable());
                assertEquals("", result.version());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void rejectsANonLoopbackEndpoint() {
        assertThrows(
                IllegalArgumentException.class,
                () -> connectivity(URI.create("https://example.com/api/version")));
    }

    private static OllamaConnectivity connectivity(URI endpoint) {
        return connectivity(endpoint, Duration.ofSeconds(2));
    }

    private static OllamaConnectivity connectivity(URI endpoint, Duration requestTimeout) {
        return new OllamaConnectivity(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(250))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                endpoint,
                requestTimeout);
    }

    private static final class LocalVersionServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalVersionServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static LocalVersionServer responding(int status, String body, Duration delay)
                throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ollama-connectivity-test-server");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/api/version", exchange -> respond(exchange, status, body, delay));
            server.start();
            return new LocalVersionServer(server, executor);
        }

        URI endpoint() {
            return URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/version");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private static void respond(
                HttpExchange exchange, int status, String body, Duration delay) throws IOException {
            try (exchange) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            }
        }
    }
}
