package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OllamaPromptClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void submitsModelAndPromptWithStreamingDisabled() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                200,
                "application/json",
                "{\"response\":\"A local answer.\",\"done\":true}")) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3:8b", 8_192),
                    new OllamaPrompt("Why local AI?"));

            assertTrue(result.successful());
            assertEquals(OllamaPromptClient.Status.SUCCESS, result.status());
            assertEquals("A local answer.", result.response());
            assertEquals("POST", server.method());
            assertEquals("application/json", server.contentType());

            JsonNode request = JSON.readTree(server.requestBody());
            assertEquals("qwen3:8b", request.get("model").textValue());
            assertEquals("Why local AI?", request.get("prompt").textValue());
            assertFalse(request.get("stream").booleanValue());
            assertEquals(8_192, request.get("options").get("num_ctx").intValue());
        }
    }

    @Test
    void treatsANonSuccessResponseAsFailedWithoutReturningItsBody() throws Exception {
        String privateResponse = "{\"error\":\"private-provider-detail\"}";
        try (LocalGenerateServer server =
                LocalGenerateServer.responding(404, "application/json", privateResponse)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("missing-model"),
                    new OllamaPrompt("private-prompt"));

            assertEquals(OllamaPromptClient.Status.REQUEST_FAILED, result.status());
            assertEquals("", result.response());
            assertFalse(result.toString().contains(privateResponse));
            assertFalse(result.toString().contains("private-prompt"));
        }
    }

    @Test
    void rejectsMalformedOrIncompleteJsonWithoutReturningProviderData() throws Exception {
        String privateResponse = "{\"private\":\"provider-detail\"}";
        try (LocalGenerateServer server =
                LocalGenerateServer.responding(200, "application/json", privateResponse)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.response());
            assertFalse(result.toString().contains(privateResponse));
        }
    }

    @Test
    void rejectsAResponseWithTerminalControlCharacters() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                200,
                "application/json",
                "{\"response\":\"safe\\u001b[31munsafe\",\"done\":true}")) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.response());
        }
    }

    @Test
    void rejectsAnUnexpectedContentType() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                200, "text/plain", "{\"response\":\"private\",\"done\":true}")) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.response());
        }
    }

    @Test
    void rejectsAnOversizedResponseBody() throws Exception {
        String oversized = "x".repeat(OllamaPromptClient.MAX_RESPONSE_BYTES + 1);
        try (LocalGenerateServer server =
                LocalGenerateServer.responding(200, "application/json", oversized)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.response());
        }
    }

    @Test
    void returnsSafelyWhenLocalOllamaIsUnavailable() throws Exception {
        URI unavailableEndpoint;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailableEndpoint = URI.create(
                    "http://127.0.0.1:" + socket.getLocalPort() + "/api/generate");
        }

        OllamaPromptClient.Result result = client(unavailableEndpoint).submit(
                new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

        assertEquals(OllamaPromptClient.Status.UNAVAILABLE, result.status());
        assertEquals("", result.response());
    }

    @Test
    void distinguishesARequestTimeoutFromUnavailableOllama() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                200,
                "application/json",
                "{\"response\":\"late\",\"done\":true}",
                Duration.ofMillis(250))) {
            OllamaPromptClient.Result result = client(
                            server.endpoint(), Duration.ofMillis(25))
                    .submit(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private-prompt"));

            assertEquals(OllamaPromptClient.Status.TIMED_OUT, result.status());
            assertEquals("", result.response());
            assertFalse(result.toString().contains("private-prompt"));
        }
    }

    @Test
    void preservesInterruptionWithoutReturningPromptData() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                200,
                "application/json",
                "{\"response\":\"private\",\"done\":true}",
                Duration.ofSeconds(1))) {
            Thread.currentThread().interrupt();
            try {
                OllamaPromptClient.Result result = client(server.endpoint()).submit(
                        new OllamaModelConfiguration("qwen3"),
                        new OllamaPrompt("private-prompt"));

                assertEquals(OllamaPromptClient.Status.INTERRUPTED, result.status());
                assertEquals("", result.response());
                assertFalse(result.toString().contains("private-prompt"));
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
                () -> client(URI.create("https://example.com/api/generate")));
    }

    private static OllamaPromptClient client(URI endpoint) {
        return client(endpoint, Duration.ofSeconds(2));
    }

    private static OllamaPromptClient client(URI endpoint, Duration requestTimeout) {
        return new OllamaPromptClient(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(250))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                endpoint,
                requestTimeout);
    }

    private static final class LocalGenerateServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> contentType = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private LocalGenerateServer(
                HttpServer server,
                ExecutorService executor,
                int status,
                String responseContentType,
                String responseBody,
                Duration delay) {
            this.server = server;
            this.executor = executor;
            server.createContext("/api/generate", exchange ->
                    respond(exchange, status, responseContentType, responseBody, delay));
        }

        static LocalGenerateServer responding(
                int status, String contentType, String body) throws IOException {
            return responding(status, contentType, body, Duration.ZERO);
        }

        static LocalGenerateServer responding(
                int status, String contentType, String body, Duration delay) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ollama-prompt-test-server");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            LocalGenerateServer local = new LocalGenerateServer(
                    server, executor, status, contentType, body, delay);
            server.start();
            return local;
        }

        URI endpoint() {
            return URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/generate");
        }

        String method() {
            return method.get();
        }

        String contentType() {
            return contentType.get();
        }

        String requestBody() {
            return requestBody.get();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private void respond(
                HttpExchange exchange,
                int status,
                String responseContentType,
                String body,
                Duration delay) throws IOException {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                requestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", responseContentType);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            }
        }
    }
}
