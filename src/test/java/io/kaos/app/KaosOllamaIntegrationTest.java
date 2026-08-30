package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaPromptClientTestSupport;
import io.kaos.ai.ollama.OllamaThinkingMode;
import io.kaos.app.config.ApplicationConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Deterministic tests across the application, Ollama client, HTTP, and output boundaries. */
class KaosOllamaIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TERMINAL = "{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"\"},\"done\":true,"
            + "\"done_reason\":\"stop\",\"total_duration\":900,"
            + "\"prompt_eval_count\":12,\"eval_count\":7,\"eval_duration\":600}\n";
    private static final OllamaModelConfiguration MODEL = new OllamaModelConfiguration(
            "qwen3:4b-instruct", 8_192, OllamaThinkingMode.OFF, 256);

    @Test
    void streamsOneAnswerAcrossTheCompleteLocalAiPath() throws Exception {
        String body = record("Integrated ") + record("answer.") + TERMINAL;
        try (LocalOllamaServer server = LocalOllamaServer.responding(200, body)) {
            KaosApplicationHarness.Result result = runPrompt(
                    server.endpoint(), "private integration prompt");

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertEquals("Integrated answer." + System.lineSeparator(), result.standardOutput());
            assertEquals("", result.errorOutput());
            assertEquals("POST", server.method());
            assertEquals("application/json", server.requestContentType());
            assertEquals("application/x-ndjson", server.accept());

            JsonNode request = JSON.readTree(server.requestBody());
            assertEquals(MODEL.modelName(), request.get("model").textValue());
            assertEquals(1, request.get("messages").size());
            assertEquals("user",
                    request.get("messages").get(0).get("role").textValue());
            assertEquals("private integration prompt",
                    request.get("messages").get(0).get("content").textValue());
            assertTrue(request.get("stream").booleanValue());
            assertFalse(request.get("think").booleanValue());
            assertEquals(MODEL.contextWindow(),
                    request.get("options").get("num_ctx").intValue());
            assertEquals(MODEL.responseTokenLimit(),
                    request.get("options").get("num_predict").intValue());
        }
    }

    @Test
    void reportsUnavailableOllamaAcrossTheApplicationAndClientBoundary() throws Exception {
        URI unavailableEndpoint;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailableEndpoint = URI.create(
                    "http://127.0.0.1:" + socket.getLocalPort() + "/api/chat");
        }

        KaosApplicationHarness.Result result = runPrompt(
                unavailableEndpoint, "private unavailable prompt");

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-001] Local Ollama could not be reached before the prompt "
                        + "response began. Start Ollama on 127.0.0.1:11434 and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private unavailable prompt"));
    }

    @Test
    void labelsPartialOutputAndHidesMalformedProviderContentAcrossTheCompletePath()
            throws Exception {
        String privateProviderContent = "private-provider-content";
        String body = record("safe prefix") + privateProviderContent + "\n";
        try (LocalOllamaServer server = LocalOllamaServer.responding(200, body)) {
            KaosApplicationHarness.Result result = runPrompt(
                    server.endpoint(), "private malformed prompt");

            assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
            assertEquals("safe prefix" + System.lineSeparator(), result.standardOutput());
            assertEquals(
                    "ERROR [KAOS-AI-002] Partial streaming output was displayed before clean "
                            + "completion. Local Ollama returned an invalid prompt response. "
                            + "Verify Ollama and retry."
                            + System.lineSeparator(),
                    result.errorOutput());
            assertFalse(result.errorOutput().contains("private malformed prompt"));
            assertFalse(result.errorOutput().contains(privateProviderContent));
        }
    }

    private static KaosApplicationHarness.Result runPrompt(URI endpoint, String prompt) {
        OllamaPromptClient client = OllamaPromptClientTestSupport.client(endpoint);
        return KaosApplicationHarness.capture((output, errorOutput) -> KaosApplication.run(
                new String[] {"ollama-prompt", prompt},
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                () -> new OllamaConnectivity.Result(
                        OllamaConnectivity.Status.REACHABLE, "test-version"),
                () -> MODEL,
                client::submit,
                output,
                errorOutput));
    }

    private static String record(String response) throws IOException {
        return JSON.writeValueAsString(java.util.Map.of(
                "message", java.util.Map.of(
                        "role", "assistant", "content", response, "thinking", ""),
                "done", false)) + "\n";
    }

    private static final class LocalOllamaServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final int status;
        private final byte[] responseBody;
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> requestContentType = new AtomicReference<>();
        private final AtomicReference<String> accept = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private LocalOllamaServer(int status, String responseBody) throws IOException {
            this.status = status;
            this.responseBody = responseBody.getBytes(StandardCharsets.UTF_8);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "kaos-ai-integration-test-server");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/api/chat", this::respond);
            server.start();
        }

        static LocalOllamaServer responding(int status, String body) throws IOException {
            return new LocalOllamaServer(status, body);
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/chat");
        }

        String method() {
            return method.get();
        }

        String requestContentType() {
            return requestContentType.get();
        }

        String accept() {
            return accept.get();
        }

        String requestBody() {
            return requestBody.get();
        }

        private void respond(HttpExchange exchange) throws IOException {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                requestBody.set(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
                exchange.sendResponseHeaders(status, 0);
                exchange.getResponseBody().write(responseBody);
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
