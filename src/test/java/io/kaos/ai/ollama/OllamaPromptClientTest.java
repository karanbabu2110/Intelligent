package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.kaos.conversation.ConversationHistory;
import io.kaos.conversation.ConversationMessage;
import io.kaos.conversation.ConversationRole;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import io.kaos.tool.httpget.HttpGetToolContract;
import io.kaos.tool.readlocalfile.ReadLocalFileToolContract;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final String TERMINAL = "{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"\"},\"done\":true,"
            + "\"done_reason\":\"stop\",\"total_duration\":900,"
            + "\"prompt_eval_count\":12,\"eval_count\":7,\"eval_duration\":600}\n";

    @Test
    void agentEvidenceSubmissionRejectsEveryUnboundedOrOutOfOrderShapeBeforeNetwork() {
        OllamaPromptClient client = new OllamaPromptClient();
        OllamaModelConfiguration model = new OllamaModelConfiguration("fixture");
        OllamaPrompt prompt = new OllamaPrompt("bounded goal");
        ReadLocalFileResult local = new ReadLocalFileResult(
                new ReadLocalFileRequest("local.md"), "local evidence");
        WebSearchResult web = new WebSearchResult(new WebSearchRequest("current"), List.of());

        assertThrows(NullPointerException.class,
                () -> client.submitWithAgentEvidence(model, prompt, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.submitWithAgentEvidence(model, prompt, List.of(local, local)));
        assertThrows(IllegalArgumentException.class,
                () -> client.submitWithAgentEvidence(model, prompt, List.of(web, local)));
        assertThrows(IllegalArgumentException.class,
                () -> client.submitWithAgentEvidence(model, prompt, List.of(local, web, web)));
    }

    @Test void additionalRegisteredToolSelectsExecutesAndContinuesWithoutConcreteDispatch() throws Exception {
        var tool = new io.kaos.tool.FixtureTool();
        var registry = new io.kaos.tool.ToolRegistry(List.of(tool));
        var model = new OllamaModelConfiguration("fixture");
        var prompt = new OllamaPrompt("Echo once");
        OllamaPromptClient.Result pending;
        try (var server = LocalChatServer.streaming(
                toolCallLine(io.kaos.tool.FixtureTool.NAME, "{\"value\":\"private argument\"}", ""), TERMINAL)) {
            pending = client(server.endpoint()).submitWithTools(model, ConversationHistory.empty(),
                    prompt, registry, List.of(io.kaos.tool.FixtureTool.NAME));
            assertTrue(pending.successful());
            assertTrue(pending.toolRequested());
            assertTrue(pending.toolRequest().isEmpty());
            assertEquals(tool.definition(), JSON.readTree(server.requestBody()).path("tools").get(0));
            assertEquals(0, tool.executions());
        }
        var permission = pending.selection().orElseThrow().prepare();
        assertThrows(IllegalStateException.class, permission::execute);
        permission.decide("approve");
        var result = permission.execute();
        assertEquals(1, tool.executions());
        try (var server = LocalChatServer.streaming(jsonLine("Done", "", false), TERMINAL)) {
            var completion = client(server.endpoint()).continueWithToolResult(model, prompt, pending, result);
            assertTrue(completion.successful());
            var request = JSON.readTree(server.requestBody());
            assertFalse(request.has("tools"));
            assertEquals("fixture_tool", request.path("messages").get(2).path("tool_name").asText());
            assertEquals("private argument", JSON.readTree(request.path("messages").get(2)
                    .path("content").asText()).path("echo").asText());
            var other = new io.kaos.tool.FixtureTool.FixtureResult("different request");
            assertThrows(IllegalArgumentException.class,
                    () -> client(server.endpoint()).continueWithToolResult(model, prompt, pending, other));
        }
        try (var server = LocalChatServer.streaming(
                toolCallLine("fixture_tool", "{\"value\":\"second\"}", ""), TERMINAL)) {
            assertFalse(client(server.endpoint()).continueWithToolResult(model, prompt, pending, result).successful());
            assertEquals(1, tool.executions());
        }
    }

    @Test void retainsSafeSelectionFailureCategoriesThroughProviderBoundary() throws Exception {
        for (var entry : java.util.Map.of(
                "unknown", io.kaos.tool.ToolSelectionException.Reason.UNKNOWN_TOOL,
                "http_get", io.kaos.tool.ToolSelectionException.Reason.DISALLOWED_TOOL,
                "read_local_file", io.kaos.tool.ToolSelectionException.Reason.MALFORMED_ARGUMENTS).entrySet()) {
            try (var server = LocalChatServer.streaming(toolCallLine(entry.getKey(), "{}", ""), TERMINAL)) {
                var result = client(server.endpoint()).submitWithLocalTools(new OllamaModelConfiguration("fixture"),
                        ConversationHistory.empty(), new OllamaPrompt("Question"));
                assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
                assertEquals(entry.getValue(), result.selectionFailure().orElseThrow());
                assertTrue(result.selection().isEmpty());
            }
        }
    }

    @Test
    void requestsStreamingAndEmitsValidatedChunksOnceInOrder() throws Exception {
        try (LocalChatServer server = LocalChatServer.streaming(
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
            assertEquals(1, request.get("messages").size());
            assertEquals("user", request.get("messages").get(0).get("role").textValue());
            assertEquals("Why local AI?",
                    request.get("messages").get(0).get("content").textValue());
            assertTrue(request.get("stream").booleanValue());
            assertFalse(request.get("think").booleanValue());
            assertFalse(request.has("tools"));
            assertEquals(8_192, request.get("options").get("num_ctx").intValue());
            assertEquals(512, request.get("options").get("num_predict").intValue());
        }
    }

    @Test
    void advertisesOnlyReadLocalFileAndReturnsOneValidatedRequest() throws Exception {
        String privatePath = "src/private/Customer.java";
        try (LocalChatServer server = LocalChatServer.streaming(
                toolCallLine("read_local_file", "{\"path\":\"" + privatePath + "\"}", ""),
                TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .submitWithReadLocalFileTool(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("Explain the selected file."));

            assertTrue(result.successful());
            assertTrue(result.toolRequested());
            assertEquals(privatePath, result.toolRequest().orElseThrow().path());
            assertEquals("", result.response());
            assertFalse(result.toString().contains(privatePath));

            JsonNode request = JSON.readTree(server.requestBody());
            assertEquals(1, request.get("tools").size());
            assertEquals(ReadLocalFileToolContract.definition(), request.get("tools").get(0));
        }
    }

    @Test
    void advertisesOnlyHttpGetAndReturnsOneValidatedRequest() throws Exception {
        String url = "https://example.com/reference?q=java";
        try (LocalChatServer server = LocalChatServer.streaming(
                toolCallLine("http_get", "{\"url\":\"" + url + "\"}", ""),
                TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .submitWithHttpGetTool(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("Find the reference."));

            assertTrue(result.successful());
            assertTrue(result.httpGetRequested());
            assertEquals(url, result.httpGetRequest().orElseThrow().url());
            assertTrue(result.toolRequest().isEmpty());
            JsonNode request = JSON.readTree(server.requestBody());
            assertEquals(1, request.get("tools").size());
            assertEquals(HttpGetToolContract.definition(), request.get("tools").get(0));
        }
    }

    @Test
    void continuesHttpGetWithUntrustedTextAndDisablesTools() throws Exception {
        HttpGetRequest request = new HttpGetRequest("https://example.com/reference");
        OllamaPromptClient.Result pending = new OllamaPromptClient.Result(
                OllamaPromptClient.Status.SUCCESS, "", "", Optional.empty(),
                Optional.of(request), OllamaPromptClient.CompletionReason.STOP,
                new OllamaPromptClient.CompletionMetrics(1, 1, 1, 1));
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("The reference says bounded.", "", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .continueWithHttpGetResult(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("Summarize it."), pending,
                            new HttpGetResult(request, "untrusted text", "text/plain"));

            assertEquals("The reference says bounded.", result.response());
            JsonNode encoded = JSON.readTree(server.requestBody());
            assertFalse(encoded.has("tools"));
            assertEquals("http_get",
                    encoded.get("messages").get(2).get("tool_name").textValue());
            assertTrue(encoded.get("messages").get(2).get("content")
                    .textValue().contains("untrusted text"));
        }
    }

    @Test
    void toolEnabledRequestMayReturnAnOrdinaryAnswerWithoutRequestingAFile() throws Exception {
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("No file is needed.", "", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .submitWithReadLocalFileTool(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("Answer without reading a file."));

            assertTrue(result.successful());
            assertFalse(result.toolRequested());
            assertEquals("No file is needed.", result.response());
        }
    }

    @Test
    void continuesOneToolRequestWithUntrustedResultAndDisablesFurtherTools() throws Exception {
        ReadLocalFileRequest toolRequest = new ReadLocalFileRequest("src/Example.java");
        ReadLocalFileResult toolResult = new ReadLocalFileResult(
                toolRequest, "final class Example {}");
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("The file defines Example.", "", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .continueWithReadLocalFileResult(
                            new OllamaModelConfiguration(
                                    "qwen3", 4_096, OllamaThinkingMode.ON),
                            new OllamaPrompt("Explain src/Example.java."),
                            new OllamaPromptClient.Result(
                                    OllamaPromptClient.Status.SUCCESS, "tool reasoning", "",
                                    java.util.Optional.of(toolRequest),
                                    OllamaPromptClient.CompletionReason.STOP,
                                    new OllamaPromptClient.CompletionMetrics(1, 1, 1, 1)),
                            toolResult);

            assertTrue(result.successful());
            assertFalse(result.toolRequested());
            assertEquals("The file defines Example.", result.response());

            JsonNode request = JSON.readTree(server.requestBody());
            assertFalse(request.has("tools"));
            assertTrue(request.get("think").booleanValue());
            assertEquals(3, request.get("messages").size());
            assertMessage(request.get("messages").get(0), "user",
                    "Explain src/Example.java.");
            JsonNode assistant = request.get("messages").get(1);
            assertMessage(assistant, "assistant", "");
            assertEquals("read_local_file",
                    assistant.get("tool_calls").get(0).get("function").get("name")
                            .textValue());
            assertEquals("src/Example.java",
                    assistant.get("tool_calls").get(0).get("function").get("arguments")
                            .get("path").textValue());
            assertEquals("tool reasoning", assistant.get("thinking").textValue());
            JsonNode tool = request.get("messages").get(2);
            assertMessage(tool, "tool",
                    JSON.writeValueAsString(ReadLocalFileToolContract.encodeResult(toolResult)));
            assertEquals("read_local_file", tool.get("tool_name").textValue());
        }
    }

    @Test
    void toolContinuationRejectsAMismatchedResultBeforeProviderSubmission() {
        ReadLocalFileRequest requested = new ReadLocalFileRequest("src/Requested.java");
        ReadLocalFileResult different = new ReadLocalFileResult(
                new ReadLocalFileRequest("src/Different.java"), "class Different {}");

        assertThrows(IllegalArgumentException.class,
                () -> client(URI.create("http://127.0.0.1:1/api/chat"))
                        .continueWithReadLocalFileResult(
                                new OllamaModelConfiguration("qwen3"),
                                new OllamaPrompt("Explain the file."),
                                new OllamaPromptClient.Result(
                                        OllamaPromptClient.Status.SUCCESS, "", "",
                                        java.util.Optional.of(requested),
                                        OllamaPromptClient.CompletionReason.STOP,
                                        new OllamaPromptClient.CompletionMetrics(1, 1, 1, 1)),
                                different));
    }

    @Test
    void ordinaryPromptRejectsAnUnadvertisedToolCall() throws Exception {
        try (LocalChatServer server = LocalChatServer.streaming(
                toolCallLine("read_local_file", "{\"path\":\"src/Main.java\"}", ""),
                TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertFalse(result.toolRequested());
        }
    }

    @Test
    void rejectsUnknownMalformedMultipleAndMixedToolCalls() throws Exception {
        String valid = toolCallLine(
                "read_local_file", "{\"path\":\"src/Main.java\"}", "");
        String multiple = valid.replace(
                "}]},\"done\":false",
                "},{\"function\":{\"name\":\"read_local_file\","
                        + "\"arguments\":{\"path\":\"src/Other.java\"}}}]},"
                        + "\"done\":false");
        assertEquals(2, JSON.readTree(multiple).get("message").get("tool_calls").size());
        for (String record : List.of(
                toolCallLine("unknown_tool", "{\"path\":\"src/Main.java\"}", ""),
                toolCallLine("read_local_file",
                        "{\"path\":\"src/Main.java\",\"extra\":true}", ""),
                multiple,
                toolCallLine("read_local_file",
                        "{\"path\":\"src/Main.java\"}", "unexpected answer"),
                toolCallLine("read_local_file",
                        "{\"path\":\"src/Main.java\"}", "   "))) {
            try (LocalChatServer server = LocalChatServer.streaming(record, TERMINAL)) {
                OllamaPromptClient.Result result = client(server.endpoint())
                        .submitWithReadLocalFileTool(
                                new OllamaModelConfiguration("qwen3"),
                                new OllamaPrompt("private prompt"));

                assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
                assertFalse(result.toolRequested());
                assertEquals("", result.response());
            }
        }
    }

    @Test
    void rejectsMoreThanOneToolCallAcrossStreamRecords() throws Exception {
        String call = toolCallLine(
                "read_local_file", "{\"path\":\"src/Main.java\"}", "");
        try (LocalChatServer server = LocalChatServer.streaming(call, call, TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .submitWithReadLocalFileTool(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private prompt"));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertFalse(result.toolRequested());
        }
    }

    @Test
    void discardsAToolRequestWhenGenerationReachesItsLengthBoundary() throws Exception {
        String terminal = TERMINAL.replace("\"stop\"", "\"length\"");
        try (LocalChatServer server = LocalChatServer.streaming(
                toolCallLine("read_local_file", "{\"path\":\"src/Main.java\"}", ""),
                terminal)) {
            OllamaPromptClient.Result result = client(server.endpoint())
                    .submitWithReadLocalFileTool(
                            new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private prompt"));

            assertEquals(OllamaPromptClient.Status.TOKEN_LIMIT_REACHED, result.status());
            assertFalse(result.toolRequested());
        }
    }

    @Test
    void sendsOrderedHistoryBeforeTheCurrentUserPrompt() throws Exception {
        ConversationHistory history = new ConversationHistory(List.of(
                new ConversationMessage(ConversationRole.USER, "First question"),
                new ConversationMessage(ConversationRole.ASSISTANT, "First answer")));
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("Context-aware answer", "", false), TERMINAL)) {
            List<String> chunks = new ArrayList<>();
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), history,
                    new OllamaPrompt("Follow-up question"), () -> { }, chunks::add);

            assertTrue(result.successful());
            assertEquals(List.of("Context-aware answer"), chunks);
            assertEquals("Context-aware answer", result.response());
            JsonNode messages = JSON.readTree(server.requestBody()).get("messages");
            assertEquals(3, messages.size());
            assertMessage(messages.get(0), "user", "First question");
            assertMessage(messages.get(1), "assistant", "First answer");
            assertMessage(messages.get(2), "user", "Follow-up question");
        }
    }

    @Test
    void sendsTheBoundedSystemInstructionBeforeHistoryAndUserPrompt() throws Exception {
        ConversationHistory history = new ConversationHistory(List.of(
                new ConversationMessage(ConversationRole.USER, "Earlier question"),
                new ConversationMessage(ConversationRole.ASSISTANT, "Earlier answer")));
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("Detailed answer", "", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), history,
                    new OllamaPrompt(
                            "Current question",
                            "Answer in detail with relevant context and explanation."));

            assertTrue(result.successful());
            JsonNode messages = JSON.readTree(server.requestBody()).get("messages");
            assertEquals(4, messages.size());
            assertMessage(messages.get(0), "system",
                    "Answer in detail with relevant context and explanation.");
            assertMessage(messages.get(1), "user", "Earlier question");
            assertMessage(messages.get(2), "assistant", "Earlier answer");
            assertMessage(messages.get(3), "user", "Current question");
        }
    }

    @Test
    void rejectsAnOversizedSerializedHistoryBeforeConnecting() throws Exception {
        ConversationMessage largestMessage = new ConversationMessage(
                ConversationRole.USER,
                "x".repeat(ConversationMessage.MAX_CONTENT_CODE_POINTS));
        ConversationHistory history = new ConversationHistory(
                java.util.Collections.nCopies(17, largestMessage));
        try (LocalChatServer server = LocalChatServer.streaming(TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), history,
                    new OllamaPrompt("follow-up"));

            assertEquals(OllamaPromptClient.Status.LOCAL_LIMIT_REACHED, result.status());
            assertNull(server.method());
        }
    }

    @Test
    void exposesFirstAnswerBeforeTheTerminalRecordIsAvailable() throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseTerminal = new CountDownLatch(1);
        try (LocalChatServer server = LocalChatServer.gated(
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
        try (LocalChatServer server = LocalChatServer.writes(writes)) {
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
            "{\"message\":{\"role\":\"assistant\",\"content\":\""
                    .getBytes(StandardCharsets.UTF_8),
            {(byte) 0xc3, (byte) 0x28},
            "\"},\"done\":false}\n".getBytes(StandardCharsets.UTF_8)
        };
        try (LocalChatServer server = LocalChatServer.writes(writes)) {
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
        try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("", "private\u001b[31mreasoning", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration(
                            "qwen3:4b", 4_096, OllamaThinkingMode.ON),
                    new OllamaPrompt("Solve this deliberately."));

            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
            assertEquals("", result.thinking());
        }
        try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(
                "{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"safe prefix\"},\"done\":false}\n",
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
        try (LocalChatServer server = LocalChatServer.streaming(
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
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},"
                        + "\"done\":true,\"done_reason\":\"stop\"}\n",
                "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},"
                        + "\"done\":true,\"done_reason\":\"unknown\","
                        + "\"total_duration\":1,\"prompt_eval_count\":1,"
                        + "\"eval_count\":1,\"eval_duration\":1}\n")) {
            try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(
                jsonLine("safe\u001b[31munsafe", "", false), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint()).submit(
                    new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
            assertEquals(OllamaPromptClient.Status.INVALID_RESPONSE, result.status());
        }
        try (LocalChatServer server = LocalChatServer.streaming(
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
        try (LocalChatServer server = LocalChatServer.streaming(oversizedRecord)) {
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
        try (LocalChatServer server = LocalChatServer.gated(
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
        try (LocalChatServer server = LocalChatServer.gated(
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
        try (LocalChatServer server = LocalChatServer.gated(
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
        try (LocalChatServer server = LocalChatServer.responding(
                404, "application/x-ndjson", "{\"error\":\"private\"}")) {
            assertEquals(OllamaPromptClient.Status.REQUEST_FAILED,
                    client(server.endpoint()).submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("hello")).status());
        }
        try (LocalChatServer server = LocalChatServer.responding(
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
            endpoint = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/api/chat");
        }
        OllamaPromptClient.Result result = client(endpoint).submit(
                new OllamaModelConfiguration("qwen3"), new OllamaPrompt("hello"));
        assertEquals(OllamaPromptClient.Status.UNAVAILABLE, result.status());
    }

    @Test
    void distinguishesAnAcceptedStreamTransportFailureFromUnavailableOllama() throws Exception {
        try (LocalChatServer server = LocalChatServer.truncatedAfter(
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
        try (LocalChatServer server = LocalChatServer.delayed(
                Duration.ofMillis(250), TERMINAL)) {
            OllamaPromptClient.Result result = client(server.endpoint(), Duration.ofMillis(25))
                    .submit(new OllamaModelConfiguration("qwen3"),
                            new OllamaPrompt("private-prompt"));
            assertEquals(OllamaPromptClient.Status.TOTAL_TIMEOUT, result.status());
        }
    }

    @Test
    void preservesInterruptionWithoutReturningPromptData() throws Exception {
        try (LocalChatServer server = LocalChatServer.delayed(
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
                () -> client(URI.create("https://example.com/api/chat")));
    }

    private static String jsonLine(String response, String thinking, boolean done)
            throws IOException {
        return JSON.writeValueAsString(java.util.Map.of(
                "message", java.util.Map.of(
                        "role", "assistant", "content", response, "thinking", thinking),
                "done", done)) + "\n";
    }

    private static String toolCallLine(String name, String arguments, String content)
            throws IOException {
        return "{\"message\":{\"role\":\"assistant\",\"content\":"
                + JSON.writeValueAsString(content)
                + ",\"tool_calls\":[{\"function\":{\"name\":"
                + JSON.writeValueAsString(name) + ",\"arguments\":" + arguments
                + "}}]},\"done\":false}\n";
    }

    private static void assertMessage(JsonNode message, String role, String content) {
        assertEquals(role, message.get("role").textValue());
        assertEquals(content, message.get("content").textValue());
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

    private static final class LocalChatServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> requestContentType = new AtomicReference<>();
        private final AtomicReference<String> accept = new AtomicReference<>();
        private final AtomicReference<String> requestBody = new AtomicReference<>();

        private LocalChatServer(int status, String contentType, byte[][] writes,
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
            server.createContext("/api/chat", exchange -> respond(exchange, status,
                    contentType, writes, initialDelay, firstWritten, releaseRest, declaredLength));
            server.start();
        }

        static LocalChatServer streaming(String... records) throws IOException {
            byte[][] writes = new byte[records.length][];
            for (int index = 0; index < records.length; index++) {
                writes[index] = records[index].getBytes(StandardCharsets.UTF_8);
            }
            return writes(writes);
        }

        static LocalChatServer writes(byte[][] writes) throws IOException {
            return new LocalChatServer(200, "application/x-ndjson", writes,
                    Duration.ZERO, null, null, 0);
        }

        static LocalChatServer responding(int status, String contentType, String body)
                throws IOException {
            return new LocalChatServer(status, contentType,
                    new byte[][] {body.getBytes(StandardCharsets.UTF_8)},
                    Duration.ZERO, null, null, 0);
        }

        static LocalChatServer delayed(Duration delay, String body) throws IOException {
            return new LocalChatServer(200, "application/x-ndjson",
                    new byte[][] {body.getBytes(StandardCharsets.UTF_8)}, delay, null, null, 0);
        }

        static LocalChatServer gated(String first, String rest, CountDownLatch firstWritten,
                CountDownLatch releaseRest) throws IOException {
            return new LocalChatServer(200, "application/x-ndjson",
                    new byte[][] {first.getBytes(StandardCharsets.UTF_8),
                        rest.getBytes(StandardCharsets.UTF_8)},
                    Duration.ZERO, firstWritten, releaseRest, 0);
        }

        static LocalChatServer truncatedAfter(String first) throws IOException {
            byte[] bytes = first.getBytes(StandardCharsets.UTF_8);
            return new LocalChatServer(200, "application/x-ndjson",
                    new byte[][] {bytes}, Duration.ZERO, null, null, bytes.length + 100L);
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/api/chat");
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
