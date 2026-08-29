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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OllamaPromptClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TERMINAL = "{\"response\":\"\",\"done\":true,"
            + "\"done_reason\":\"stop\",\"total_duration\":900,"
            + "\"prompt_eval_count\":12,\"eval_count\":7,\"eval_duration\":600}\n";

    @Test
    void requestsStreamingAndEmitsValidatedChunksOnceInOrder() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("A local ", "", false),
                jsonLine("answer.", "", false),
                TERMINAL)) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3:8b", 8_192),
                    new OllamaPrompt("Why local AI?"),
                    chunks::add);

            assertTrue(result.successful());
            assertEquals(List.of("A local ", "answer."), chunks);
            assertEquals("A local answer.", result.response());
            assertEquals(OllamaPromptClient.CompletionReason.STOP,
                    result.completionReason());
            assertEquals(new OllamaPromptClient.CompletionMetrics(900, 12, 7, 600),
                    result.metrics());
            assertEquals("POST", server.method());
            assertEquals("application/json", server.requestContentType());
            assertEquals("application/x-ndjson", server.accept());

            JsonNode request = JSON.readTree(server.requestBody());
            assertEquals("qwen3:8b", request.get("model").textValue());
            assertEquals("Why local AI?", request.get("prompt").textValue());
            assertTrue(request.get("stream").booleanValue());
            assertFalse(request.get("think").booleanValue());
            assertEquals(8_192, request.get("options").get("num_ctx").intValue());
            assertEquals(512, request.get("options").get("num_predict").intValue());
        }
    }

    @Test
    void exposesFirstAnswerBeforeTheTerminalRecordIsAvailable() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminal = new CountDownLatch(1);
        try (LocalGenerateServer server = LocalGenerateServer.gated(
                jsonLine("first", "", false), TERMINAL, firstWritten, releaseTerminal);
                ExecutorService clientExecutor = Executors.newSingleThreadExecutor()) {
            CountDownLatch firstObserved = new CountDownLatch(1);
            List<String> chunks = new ArrayList<>();
            Future<OllamaPromptClient.Result> future = clientExecutor.submit(() ->
                    client(server.endpoint()).submit(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("hello"),
                            chunk -> {
                                chunks.add(chunk);
                                firstObserved.countDown();
                            }));

            assertTrue(firstWritten.await(2, TimeUnit.SECONDS));
            assertTrue(firstObserved.await(2, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            assertEquals(List.of("first"), chunks);

            releaseTerminal.countDown();
            assertTrue(future.get(2, TimeUnit.SECONDS).successful());
        }
    }

    @Test
    void preservesUtf8CharactersSplitAcrossNetworkWrites() throws Exception {
        String record = jsonLine("A 🌍 answer", "", false);
        byte[] bytes = record.getBytes(StandardCharsets.UTF_8);
        int emoji = indexOf(bytes, "🌍".getBytes(StandardCharsets.UTF_8));
        byte[][] writes = {
            slice(bytes, 0, emoji + 1),
            slice(bytes, emoji + 1, emoji + 3),
            slice(bytes, emoji + 3, bytes.length),
            TERMINAL.getBytes(StandardCharsets.UTF_8)
        };
        try (LocalGenerateServer server = LocalGenerateServer.writes(writes)) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"),
                    new OllamaPrompt("hello"), chunks::add);

            assertTrue(result.successful());
            assertEquals(List.of("A 🌍 answer"), chunks);
            assertEquals("A 🌍 answer", result.response());
        }
    }

    @Test
    void rejectsMalformedUtf8WithoutEmittingReplacementText() throws Exception {
        byte[][] writes = {
            "{\"response\":\"".getBytes(StandardCharsets.UTF_8),
            {(byte) 0xc3, (byte) 0x28},
            "\",\"done\":false}\n".getBytes(StandardCharsets.UTF_8)
        };
        try (LocalGenerateServer server = LocalGenerateServer.writes(writes)) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"),
                    new OllamaPrompt("hello"),
                    chunks::add);

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals(List.of(), chunks);
        }
    }

    @Test
    void keepsThinkingSeparateAndNeverEmitsItAsAnswerContent() throws Exception {
        String firstPrivateThinking = "private reasoning ";
        String secondPrivateThinking = "trace";
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("", firstPrivateThinking, false),
                jsonLine("", secondPrivateThinking, false),
                jsonLine("Final answer.", "", false),
                TERMINAL)) {
            List<String> chunks = new ArrayList<>();
            AtomicInteger thinkingSignals = new AtomicInteger();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3:4b", 4_096, OllamaThinkingMode.ON),
                    new OllamaPrompt("Solve this deliberately."),
                    thinkingSignals::incrementAndGet,
                    chunks::add);

            assertEquals(List.of("Final answer."), chunks);
            assertEquals(1, thinkingSignals.get());
            assertEquals(firstPrivateThinking + secondPrivateThinking, result.thinking());
            assertFalse(result.toString().contains(firstPrivateThinking));
        }
    }

    @Test
    void thinkingOffRejectsUnexpectedThinkingWithoutDisplayingIt() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("", "unexpected trace", false),
                jsonLine("Final answer.", "", false), TERMINAL)) {
            AtomicInteger thinkingSignals = new AtomicInteger();
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3:4b-instruct"),
                    new OllamaPrompt("Answer ordinarily."),
                    thinkingSignals::incrementAndGet,
                    chunks::add);

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals(0, thinkingSignals.get());
            assertEquals(List.of(), chunks);
            assertEquals("", result.thinking());
        }
    }

    @Test
    void rejectsThinkingThatArrivesAfterAnswerOutputStarts() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("First answer chunk", "", false),
                jsonLine("", "late private reasoning", false),
                TERMINAL)) {
            AtomicInteger thinkingSignals = new AtomicInteger();
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration(
                            "qwen3:4b", 4_096, OllamaThinkingMode.ON),
                    new OllamaPrompt("Solve this deliberately."),
                    thinkingSignals::incrementAndGet,
                    chunks::add);

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals(0, thinkingSignals.get());
            assertEquals(List.of("First answer chunk"), chunks);
            assertEquals("", result.thinking());
        }
    }

    @Test
    void rejectsUnsafeOrOversizedThinkingWithoutEmittingIt() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("", "private\u001b[31mreasoning", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration(
                            "qwen3:4b", 4_096, OllamaThinkingMode.ON),
                    new OllamaPrompt("Solve this deliberately."));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.thinking());
        }
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("", "x".repeat(
                        OllamaPromptClient.MAX_THINKING_CODE_POINTS + 1), false),
                TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration(
                            "qwen3:4b", 4_096, OllamaThinkingMode.ON),
                    new OllamaPrompt("Solve this deliberately."));

            assertEquals(OllamaPromptClient.Status.LOCAL_LIMIT_REACHED, result.status());
            assertEquals("", result.thinking());
        }
    }

    @Test
    void malformedRecordFailsWithoutReturningProviderData() throws Exception {
        String privateData = "private-provider-detail";
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                "{\"response\":\"safe prefix\",\"done\":false}\n",
                "{\"private\":\"" + privateData + "\"}\n")) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"),
                    new OllamaPrompt("private-prompt"), chunks::add);

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals(List.of("safe prefix"), chunks);
            assertEquals("", result.response());
            assertFalse(result.toString().contains(privateData));
            assertFalse(result.toString().contains("private-prompt"));
        }
    }

    @Test
    void incompleteStreamWithoutTerminalRecordFails() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("partial", "", false))) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.response());
        }
    }

    @Test
    void terminalRecordRequiresSupportedReasonAndMetrics() throws Exception {
        for (String terminal : List.of(
                "{\"response\":\"\",\"done\":true,\"done_reason\":\"stop\"}\n",
                "{\"response\":\"\",\"done\":true,\"done_reason\":\"unknown\","
                        + "\"total_duration\":1,\"prompt_eval_count\":1,"
                        + "\"eval_count\":1,\"eval_duration\":1}\n")) {
            try (LocalGenerateServer server = LocalGenerateServer.streaming(
                    jsonLine("answer", "", false), terminal)) {
                OllamaPromptClient.Result result = client(server.endpoint()).submit(
                        new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
                assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            }
        }
    }

    @Test
    void lengthCompletionIsReportedWithoutReturningAssembledData() throws Exception {
        String terminal = TERMINAL.replace("\"stop\"", "\"length\"");
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("partial answer", "", false), terminal)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
            assertEquals(OllamaPromptClient.Status.TOKEN_LIMIT_REACHED, result.status());
            assertEquals(OllamaPromptClient.CompletionReason.LENGTH,
                    result.completionReason());
            assertEquals(7, result.metrics().generatedTokenCount());
            assertEquals("", result.response());
        }
    }

    @Test
    void rejectsUnsafeOrOversizedGeneratedContent() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("safe\u001b[31munsafe", "", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
        }
        try (LocalGenerateServer server = LocalGenerateServer.streaming(
                jsonLine("x".repeat(
                        OllamaPromptClient.MAX_RESPONSE_CODE_POINTS + 1), "", false),
                TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
            assertEquals(OllamaPromptClient.Status.LOCAL_LIMIT_REACHED, result.status());
        }
    }

    @Test
    void rejectsAProviderStreamThatExceedsItsByteBound() throws Exception {
        String oversizedRecord = jsonLine(
                "x".repeat(OllamaPromptClient.MAX_RESPONSE_BYTES), "", false);
        try (LocalGenerateServer server = LocalGenerateServer.streaming(oversizedRecord)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

            assertEquals(OllamaPromptClient.Status.LOCAL_LIMIT_REACHED, result.status());
            assertEquals("", result.response());
        }
    }

    @Test
    void cancelsAnActiveStreamWhenTheCallingThreadIsInterrupted() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminal = new CountDownLatch(1);
        try (LocalGenerateServer server = LocalGenerateServer.gated(
                jsonLine("partial", "", false), TERMINAL, firstWritten, releaseTerminal);
                ExecutorService clientExecutor = Executors.newSingleThreadExecutor()) {
            AtomicReference<Thread> clientThread = new AtomicReference<>();
            AtomicReference<Boolean> interruptedAtReturn = new AtomicReference<>(false);
            CountDownLatch firstObserved = new CountDownLatch(1);
            List<String> chunks = new ArrayList<>();
            Future<OllamaPromptClient.Result> future = clientExecutor.submit(() -> {
                clientThread.set(Thread.currentThread());
                OllamaPromptClient.Result result = client(server.endpoint()).submit(
                        new OllamaModelConfiguration("qwen3"),
                        new OllamaPrompt("private-prompt"), chunk -> {
                            chunks.add(chunk);
                            firstObserved.countDown();
                        });
                interruptedAtReturn.set(Thread.currentThread().isInterrupted());
                return result;
            });

            assertTrue(firstWritten.await(2, TimeUnit.SECONDS));
            assertTrue(firstObserved.await(2, TimeUnit.SECONDS));
            clientThread.get().interrupt();

            OllamaPromptClient.Result result = future.get(2, TimeUnit.SECONDS);
            assertEquals(OllamaPromptClient.Status.INTERRUPTED, result.status());
            assertTrue(interruptedAtReturn.get());
            assertEquals(List.of("partial"), chunks);
            releaseTerminal.countDown();
        }
    }

    @Test
    void stopsAStreamThatMakesNoProgressWithinTheInactivityBound() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminal = new CountDownLatch(1);
        try (LocalGenerateServer server = LocalGenerateServer.gated(
                jsonLine("partial", "", false), TERMINAL, firstWritten, releaseTerminal)) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(
                    server.endpoint(), Duration.ofSeconds(2), Duration.ofMillis(50))
                    .submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private-prompt"), chunks::add);

            assertEquals(OllamaPromptClient.Status.INACTIVITY_TIMEOUT, result.status());
            assertEquals(List.of("partial"), chunks);
            releaseTerminal.countDown();
        }
    }

    @Test
    void stopsAtTheTotalDeadlineBeforeTheLongerInactivityBound() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminal = new CountDownLatch(1);
        try (LocalGenerateServer server = LocalGenerateServer.gated(
                jsonLine("partial", "", false), TERMINAL, firstWritten, releaseTerminal)) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(
                    server.endpoint(), Duration.ofMillis(500), Duration.ofSeconds(2))
                    .submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private-prompt"), chunks::add);

            assertEquals(OllamaPromptClient.Status.TOTAL_TIMEOUT, result.status());
            assertEquals(List.of("partial"), chunks);
            releaseTerminal.countDown();
        }
    }

    @Test
    void rejectsNonSuccessAndUnexpectedContentType() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                404, "application/x-ndjson", "{\"error\":\"private\"}")) {
            assertEquals(OllamaPromptClient.Status.REQUEST_FAILED,
                    client(server.endpoint()).submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("hello")).status());
        }
        try (LocalGenerateServer server = LocalGenerateServer.responding(
                200, "text/plain", TERMINAL)) {
            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE,
                    client(server.endpoint()).submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("hello")).status());
        }
    }

    @Test
    void returnsSafelyWhenLocalOllamaIsUnavailable() throws Exception {
        URI endpoint;
        try (ServerSocket socket = new ServerSocket(0)) {
            endpoint = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/api/generate");
        }
        OllamaPromptClient.Result result = client(endpoint).submit(
                new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
        assertEquals(OllamaPromptClient.Status.UNAVAILABLE, result.status());
    }

    @Test
    void distinguishesAnAcceptedStreamTransportFailureFromUnavailableOllama() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.truncatedAfter(
                jsonLine("partial", "", false))) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"),
                    new OllamaPrompt("private-prompt"), chunks::add);

            assertEquals(OllamaPromptClient.Status.STREAM_FAILED, result.status());
            assertEquals(List.of("partial"), chunks);
        }
    }

    @Test
    void distinguishesARequestTimeoutFromUnavailableOllama() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.delayed(
                Duration.ofMillis(250), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint(), Duration.ofMillis(25))
                    .submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private-prompt"));
            assertEquals(OllamaPromptClient.Status.TOTAL_TIMEOUT, result.status());
        }
    }

    @Test
    void preservesInterruptionWithoutReturningPromptData() throws Exception {
        try (LocalGenerateServer server = LocalGenerateServer.delayed(
                Duration.ofSeconds(1), TERMINAL)) {
            Thread.currentThread().interrupt();
            try {
                OllamaPromptClient.Result result = client(server.endpoint()).submit(
                        new OllamaModelConfiguration("qwen3"),
                        new OllamaPrompt("private-prompt"));
                assertEquals(OllamaPromptClient.Status.INTERRUPTED, result.status());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void rejectsANonLoopbackEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> client(URI.create("https://example.com/api/generate")));
    }

    private static String jsonLine(String response, String thinking, boolean done)
            throws IOException {
        return JSON.writeValueAsString(java.util.Map.of(
                "response", response, "thinking", thinking, "done", done)) + "\n";
    }

    private static OllamaPromptClient client(URI endpoint) {
        return client(endpoint, Duration.ofSeconds(2));
    }

    private static OllamaPromptClient client(URI endpoint, Duration timeout) {
        return new OllamaPromptClient(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(250))
                .followRedirects(HttpClient.Redirect.NEVER).build(), endpoint, timeout);
    }

    private static OllamaPromptClient client(
            URI endpoint, Duration timeout, Duration inactivityTimeout) {
        return new OllamaPromptClient(HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(250))
                .followRedirects(HttpClient.Redirect.NEVER).build(), endpoint, timeout,
                inactivityTimeout);
    }

    private static int indexOf(byte[] source, byte[] target) {
        outer: for (int index = 0; index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) continue outer;
            }
            return index;
        }
        throw new AssertionError("target bytes not found");
    }

    private static byte[] slice(byte[] source, int from, int to) {
        return java.util.Arrays.copyOfRange(source, from, to);
    }

    private static final class LocalGenerateServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> requestContentType = new AtomicReference<>();
        private final AtomicReference<String> accept = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private LocalGenerateServer(int status, String contentType, byte[][] writes,
                Duration initialDelay, CountDownLatch firstWritten, CountDownLatch releaseRest,
                long declaredLength)
                throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ollama-prompt-test-server");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/api/generate", exchange -> respond(exchange, status,
                    contentType, writes, initialDelay, firstWritten, releaseRest, declaredLength));
            server.start();
        }

        static LocalGenerateServer streaming(String... records) throws IOException {
            byte[][] writes = new byte[records.length][];
            for (int index = 0; index < records.length; index++) {
                writes[index] = records[index].getBytes(StandardCharsets.UTF_8);
            }
            return writes(writes);
        }

        static LocalGenerateServer writes(byte[][] writes) throws IOException {
            return new LocalGenerateServer(200, "application/x-ndjson", writes,
                    Duration.ZERO, null, null, 0);
        }

        static LocalGenerateServer responding(int status, String contentType, String body)
                throws IOException {
            return new LocalGenerateServer(status, contentType,
                    new byte[][] {body.getBytes(StandardCharsets.UTF_8)},
                    Duration.ZERO, null, null, 0);
        }

        static LocalGenerateServer delayed(Duration delay, String body) throws IOException {
            return new LocalGenerateServer(200, "application/x-ndjson",
                    new byte[][] {body.getBytes(StandardCharsets.UTF_8)}, delay, null, null, 0);
        }

        static LocalGenerateServer gated(String first, String rest, CountDownLatch firstWritten,
                CountDownLatch releaseRest) throws IOException {
            return new LocalGenerateServer(200, "application/x-ndjson",
                    new byte[][] {first.getBytes(StandardCharsets.UTF_8),
                        rest.getBytes(StandardCharsets.UTF_8)},
                    Duration.ZERO, firstWritten, releaseRest, 0);
        }

        static LocalGenerateServer truncatedAfter(String first) throws IOException {
            byte[] bytes = first.getBytes(StandardCharsets.UTF_8);
            return new LocalGenerateServer(200, "application/x-ndjson",
                    new byte[][] {bytes}, Duration.ZERO, null, null, bytes.length + 100L);
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/generate");
        }

        String method() { return method.get(); }
        String requestContentType() { return requestContentType.get(); }
        String accept() { return accept.get(); }
        String requestBody() { return requestBody.get(); }

        private void respond(HttpExchange exchange, int status, String contentType,
                byte[][] writes, Duration initialDelay, CountDownLatch firstWritten,
                CountDownLatch releaseRest, long declaredLength) throws IOException {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                requestContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                sleep(initialDelay);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, declaredLength);
                for (int index = 0; index < writes.length; index++) {
                    exchange.getResponseBody().write(writes[index]);
                    exchange.getResponseBody().flush();
                    if (index == 0 && firstWritten != null) {
                        firstWritten.countDown();
                        await(releaseRest);
                    }
                }
            }
        }

        private static void sleep(Duration delay) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        private static void await(CountDownLatch latch) {
            try {
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
