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
import io.kaos.conversation.ConversationMessage;
import io.kaos.conversation.ConversationRole;
import io.kaos.conversation.SqliteConversationStore;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Deterministic tests across the application, Ollama client, HTTP, and output boundaries. */
class KaosOllamaIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TERMINAL = "{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"\"},\"done\":true,"
            + "\"done_reason\":\"stop\",\"total_duration\":900,"
            + "\"prompt_eval_count\":12,\"eval_count\":7,\"eval_duration\":600}\n";
    private static final OllamaModelConfiguration MODEL = new OllamaModelConfiguration(
            "qwen3:4b-instruct", 8_192, OllamaThinkingMode.OFF, 256);

    @TempDir
    Path temporaryDirectory;

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

    @Test
    void preservesOnlyTheSelectedConversationAcrossTheCompleteLocalAiPath()
            throws Exception {
        try (LocalOllamaServer server = LocalOllamaServer.responding(
                200,
                record("First answer") + TERMINAL,
                record("Second answer") + TERMINAL,
                record("Follow-up answer") + TERMINAL)) {
            KaosApplicationHarness.Result result = runConversation(
                    server.endpoint(),
                    "First topic\n/new\nSecond topic\n/select 1\nFollow up first\n/exit\n");

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertEquals(1, occurrences(result.standardOutput(), "First answer"));
            assertEquals(1, occurrences(result.standardOutput(), "Second answer"));
            assertEquals(1, occurrences(result.standardOutput(), "Follow-up answer"));
            assertEquals("", result.errorOutput());

            List<String> requests = server.requestBodies();
            assertEquals(3, requests.size());
            JsonNode firstMessages = requestMessages(requests.get(0));
            assertEquals(1, firstMessages.size());
            assertMessage(firstMessages.get(0), "user", "First topic");
            JsonNode secondMessages = requestMessages(requests.get(1));
            assertEquals(1, secondMessages.size());
            assertMessage(secondMessages.get(0), "user", "Second topic");
            JsonNode thirdMessages = requestMessages(requests.get(2));
            assertEquals(3, thirdMessages.size());
            assertMessage(thirdMessages.get(0), "user", "First topic");
            assertMessage(thirdMessages.get(1), "assistant", "First answer");
            assertMessage(thirdMessages.get(2), "user", "Follow up first");
        }
    }

    @Test
    void excludesAPartialMalformedTurnFromTheNextRealRequest() throws Exception {
        String privateProviderContent = "private-provider-content";
        try (LocalOllamaServer server = LocalOllamaServer.responding(
                200,
                record("safe prefix") + privateProviderContent + "\n",
                record("Clean answer") + TERMINAL)) {
            KaosApplicationHarness.Result result = runConversation(
                    server.endpoint(),
                    "private failed prompt\nClean recovery prompt\n/exit\n");

            assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
            assertTrue(result.standardOutput().contains("safe prefix"));
            assertTrue(result.standardOutput().contains("Clean answer"));
            assertFalse(result.standardOutput().contains(privateProviderContent));
            assertTrue(result.errorOutput().contains("ERROR [KAOS-AI-002]"));
            assertFalse(result.errorOutput().contains("private failed prompt"));
            assertFalse(result.errorOutput().contains(privateProviderContent));

            List<String> requests = server.requestBodies();
            assertEquals(2, requests.size());
            JsonNode recoveryMessages = requestMessages(requests.get(1));
            assertEquals(1, recoveryMessages.size());
            assertMessage(recoveryMessages.get(0), "user", "Clean recovery prompt");
        }
    }

    @Test
    void restoresPersistedHistoryAcrossSeparateCompleteLocalAiRuns() throws Exception {
        try (LocalOllamaServer server = LocalOllamaServer.responding(
                200,
                record("First persisted answer") + TERMINAL,
                record("Restarted answer") + TERMINAL)) {
            KaosApplicationHarness.Result firstRun = runConversation(
                    server.endpoint(), "Remember this durable fact\n/exit\n");
            KaosApplicationHarness.Result secondRun = runConversation(
                    server.endpoint(), "What did I ask before?\n/exit\n");

            assertEquals(KaosApplication.SUCCESS, firstRun.exitCode());
            assertEquals(KaosApplication.SUCCESS, secondRun.exitCode());
            assertEquals("", firstRun.errorOutput());
            assertEquals("", secondRun.errorOutput());
            assertTrue(secondRun.standardOutput().contains(
                    "Restored 1 conversation. Conversation 1 selected."));

            List<String> requests = server.requestBodies();
            assertEquals(2, requests.size());
            JsonNode restartedMessages = requestMessages(requests.get(1));
            assertEquals(3, restartedMessages.size());
            assertMessage(restartedMessages.get(0), "user", "Remember this durable fact");
            assertMessage(restartedMessages.get(1), "assistant", "First persisted answer");
            assertMessage(restartedMessages.get(2), "user", "What did I ask before?");

            SqliteConversationStore store = new SqliteConversationStore(
                    temporaryDirectory.resolve("conversations.db"));
            assertEquals(List.of(
                    new ConversationMessage(
                            ConversationRole.USER, "Remember this durable fact"),
                    new ConversationMessage(
                            ConversationRole.ASSISTANT, "First persisted answer"),
                    new ConversationMessage(
                            ConversationRole.USER, "What did I ask before?"),
                    new ConversationMessage(
                            ConversationRole.ASSISTANT, "Restarted answer")),
                    store.conversationHistory(1).messages());
        }
    }

    @Test
    void excludesAFailedPartialTurnFromDurableHistoryAfterAnotherRestart()
            throws Exception {
        String privateProviderContent = "private-malformed-persistence-content";
        try (LocalOllamaServer server = LocalOllamaServer.responding(
                200,
                record("Durable answer") + TERMINAL,
                record("visible partial") + privateProviderContent + "\n",
                record("Recovery answer") + TERMINAL)) {
            KaosApplicationHarness.Result committedRun = runConversation(
                    server.endpoint(), "Committed prompt\n/exit\n");
            KaosApplicationHarness.Result failedRun = runConversation(
                    server.endpoint(), "private failed persistence prompt\n");

            assertEquals(KaosApplication.SUCCESS, committedRun.exitCode());
            assertEquals(KaosApplication.APPLICATION_ERROR, failedRun.exitCode());
            assertTrue(failedRun.standardOutput().contains("visible partial"));
            assertFalse(failedRun.standardOutput().contains(privateProviderContent));
            assertTrue(failedRun.errorOutput().contains("ERROR [KAOS-AI-002]"));
            assertFalse(failedRun.errorOutput().contains("private failed persistence prompt"));
            assertFalse(failedRun.errorOutput().contains(privateProviderContent));

            SqliteConversationStore store = new SqliteConversationStore(
                    temporaryDirectory.resolve("conversations.db"));
            assertEquals(List.of(
                    new ConversationMessage(ConversationRole.USER, "Committed prompt"),
                    new ConversationMessage(ConversationRole.ASSISTANT, "Durable answer")),
                    store.conversationHistory(1).messages());

            KaosApplicationHarness.Result recoveryRun = runConversation(
                    server.endpoint(), "Recovery prompt\n/exit\n");

            assertEquals(KaosApplication.SUCCESS, recoveryRun.exitCode());
            assertEquals("", recoveryRun.errorOutput());
            List<String> requests = server.requestBodies();
            assertEquals(3, requests.size());
            JsonNode recoveryMessages = requestMessages(requests.get(2));
            assertEquals(3, recoveryMessages.size());
            assertMessage(recoveryMessages.get(0), "user", "Committed prompt");
            assertMessage(recoveryMessages.get(1), "assistant", "Durable answer");
            assertMessage(recoveryMessages.get(2), "user", "Recovery prompt");
            assertFalse(requests.get(2).contains("private failed persistence prompt"));
            assertFalse(requests.get(2).contains("visible partial"));
            assertFalse(requests.get(2).contains(privateProviderContent));
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

    private KaosApplicationHarness.Result runConversation(URI endpoint, String input) {
        OllamaPromptClient client = OllamaPromptClientTestSupport.client(endpoint);
        return KaosApplicationHarness.captureInput(input,
                (testInput, output, errorOutput) -> KaosApplication.run(
                        new String[] {"conversation"},
                        new ApplicationConfiguration(
                                ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                        () -> new OllamaConnectivity.Result(
                                OllamaConnectivity.Status.REACHABLE, "test-version"),
                        () -> MODEL,
                        client::submit,
                        () -> temporaryDirectory.resolve("conversations.db"),
                        testInput,
                        output,
                        errorOutput));
    }

    private static JsonNode requestMessages(String requestBody) throws IOException {
        return JSON.readTree(requestBody).get("messages");
    }

    private static void assertMessage(JsonNode message, String role, String content) {
        assertEquals(role, message.get("role").textValue());
        assertEquals(content, message.get("content").textValue());
    }

    private static int occurrences(String value, String content) {
        return (value.length() - value.replace(content, "").length()) / content.length();
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
        private final List<byte[]> responseBodies;
        private final AtomicInteger responseIndex = new AtomicInteger();
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> requestContentType = new AtomicReference<>();
        private final AtomicReference<String> accept = new AtomicReference<>();
        private final List<String> requestBodies = new ArrayList<>();

        private LocalOllamaServer(int status, String... responseBodies) throws IOException {
            if (responseBodies.length == 0) {
                throw new IllegalArgumentException("at least one response body is required");
            }
            this.status = status;
            this.responseBodies = java.util.Arrays.stream(responseBodies)
                    .map(body -> body.getBytes(StandardCharsets.UTF_8))
                    .toList();
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

        static LocalOllamaServer responding(int status, String... bodies) throws IOException {
            return new LocalOllamaServer(status, bodies);
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
            return requestBodies.isEmpty()
                    ? null
                    : requestBodies.get(requestBodies.size() - 1);
        }

        List<String> requestBodies() {
            return List.copyOf(requestBodies);
        }

        private void respond(HttpExchange exchange) throws IOException {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                requestBodies.add(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
                int index = responseIndex.getAndIncrement();
                int responseStatus = index < responseBodies.size() ? status : 500;
                byte[] responseBody = index < responseBodies.size()
                        ? responseBodies.get(index)
                        : new byte[0];
                exchange.sendResponseHeaders(responseStatus, 0);
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
