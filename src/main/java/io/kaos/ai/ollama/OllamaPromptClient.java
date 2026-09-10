package io.kaos.ai.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaos.conversation.ConversationHistory;
import io.kaos.conversation.ConversationMessage;
import io.kaos.conversation.ConversationRole;
import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.ToolResult;
import io.kaos.tool.ToolSelection;
import io.kaos.tool.ToolSelectionException;
import io.kaos.tool.ToolSelector;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import io.kaos.tool.httpget.HttpGetToolContract;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import io.kaos.tool.readlocalfile.ReadLocalFileToolContract;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import io.kaos.tool.websearch.WebSearchToolContract;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Submits one bounded streaming chat request to local Ollama. */
public final class OllamaPromptClient {
    public static final URI LOCAL_CHAT_ENDPOINT =
            URI.create("http://127.0.0.1:11434/api/chat");
    public static final int MAX_REQUEST_BYTES = 1_048_576;
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    public static final int MAX_RESPONSE_CODE_POINTS = 65_536;
    public static final int MAX_THINKING_CODE_POINTS = 65_536;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final ToolRegistry tools;
    private final HttpClient httpClient;
    private final URI chatEndpoint;
    private final Duration requestTimeout;
    private final Duration inactivityTimeout;

    /** Creates a client restricted to the fixed local Ollama chat endpoint. */
    public OllamaPromptClient() { this(StandardTools.create()); }

    public OllamaPromptClient(ToolRegistry tools) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                LOCAL_CHAT_ENDPOINT, REQUEST_TIMEOUT, INACTIVITY_TIMEOUT, tools);
    }

    OllamaPromptClient(HttpClient httpClient, URI chatEndpoint, Duration requestTimeout) {
        this(httpClient, chatEndpoint, requestTimeout, requestTimeout);
    }

    OllamaPromptClient(HttpClient httpClient, URI chatEndpoint, Duration requestTimeout,
            Duration inactivityTimeout) {
        this(httpClient, chatEndpoint, requestTimeout, inactivityTimeout, StandardTools.create());
    }

    OllamaPromptClient(HttpClient httpClient, URI chatEndpoint, Duration requestTimeout,
            Duration inactivityTimeout, ToolRegistry tools) {
        this.tools = Objects.requireNonNull(tools, "tools");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.chatEndpoint = requireLoopbackHttpEndpoint(chatEndpoint);
        this.requestTimeout = requirePositiveTimeout(requestTimeout);
        this.inactivityTimeout = requirePositiveTimeout(inactivityTimeout);
    }

    /** Generates one streamed response without exposing progressive chunks to the caller. */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt) {
        return submit(model, ConversationHistory.empty(), prompt, () -> { }, ignored -> { });
    }

    /** Generates one response constrained by a caller-owned JSON Schema and advertises no tools. */
    public Result submitWithStructuredOutput(OllamaModelConfiguration model, OllamaPrompt prompt,
            JsonNode format) {
        if (format == null || !format.isObject() || format.isEmpty()) {
            throw new IllegalArgumentException("structured output format must be a JSON object");
        }
        return submit(model, ConversationHistory.empty(), prompt, () -> { }, ignored -> { },
                new ToolSelector(tools, List.of()), format.deepCopy());
    }

    /** Synthesizes from one validated agent run with no advertised tool authority. */
    public Result submitWithAgentEvidence(OllamaModelConfiguration model, OllamaPrompt prompt,
            List<ToolResult<?>> evidence) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(prompt, "prompt");
        List<ToolResult<?>> bounded = validateAgentEvidence(evidence);
        try {
            List<ChatMessage> messages = new ArrayList<>();
            if (!prompt.systemInstruction().isEmpty()) {
                messages.add(ChatMessage.text("system", prompt.systemInstruction()));
            }
            messages.add(ChatMessage.text("user", prompt.text()));
            for (ToolResult<?> result : bounded) {
                messages.add(ChatMessage.toolResult(result.toolName(),
                        JSON.writeValueAsString(result.modelContent())));
            }
            BoundedRequestOutputStream output = new BoundedRequestOutputStream(MAX_REQUEST_BYTES);
            JSON.writeValue(output, new ChatRequest(model.modelName(), List.copyOf(messages),
                    List.of(), true, model.thinkingMode().enabled(), null,
                    new GenerateOptions(model.contextWindow(), model.responseTokenLimit())));
            return submitRequest(output.toByteArray(), model.thinkingMode(),
                    () -> { }, ignored -> { }, new ToolSelector(tools, List.of()));
        } catch (RequestLimitException exception) {
            return Result.failed(Status.LOCAL_LIMIT_REACHED);
        } catch (IOException exception) {
            if (isCausedByRequestLimit(exception)) return Result.failed(Status.LOCAL_LIMIT_REACHED);
            throw new IllegalStateException("Unable to encode validated agent evidence.", exception);
        }
    }

    /** Advertises only {@code read_local_file} and returns its request without executing it. */
    public Result submitWithReadLocalFileTool(
            OllamaModelConfiguration model, OllamaPrompt prompt) {
        return submit(model, ConversationHistory.empty(), prompt,
                () -> { }, ignored -> { }, StandardTools.READ_LOCAL_FILE);
    }

    /** Advertises only {@code http_get} and returns its request without executing it. */
    public Result submitWithHttpGetTool(
            OllamaModelConfiguration model, OllamaPrompt prompt) {
        return submit(model, ConversationHistory.empty(), prompt,
                () -> { }, ignored -> { }, StandardTools.HTTP_GET);
    }


    /** Advertises file and search together; accepts at most one call across the stream. */
    public Result submitWithLocalTools(OllamaModelConfiguration model,
            ConversationHistory history, OllamaPrompt prompt) {
        return submit(model, history, prompt, () -> { }, ignored -> { }, StandardTools.LOCAL);
    }

    /** Advertises only the caller's explicit operation scope. */
    public Result submitWithTools(OllamaModelConfiguration model, ConversationHistory history,
            OllamaPrompt prompt, ToolRegistry registry, List<String> allowedTools) {
        // The same registry supplies both advertisement and resolution for this request.
        return submit(model, history, prompt, () -> { }, ignored -> { }, new ToolSelector(registry, allowedTools));
    }

    /** Continues one matching result with no advertised tools and no execution loop. */
    public Result continueWithToolResult(OllamaModelConfiguration model, OllamaPrompt prompt,
            Result pending, ToolResult<?> result) {
        ToolSelection selection = pending.selection().orElseThrow(() ->
                new IllegalArgumentException("Continuation requires a pending tool selection."));
        if (!pending.successful() || !selection.matches(result)) {
            throw new IllegalArgumentException("Continuation requires the matching tool result.");
        }
        try {
            var function = JSON.createObjectNode().put("name", selection.name())
                    .set("arguments", selection.arguments());
            var call = JSON.createObjectNode().put("type", "function").set("function", function);
            List<ChatMessage> messages = new ArrayList<>();
            if (!prompt.systemInstruction().isEmpty()) {
                messages.add(ChatMessage.text("system", prompt.systemInstruction()));
            }
            messages.add(ChatMessage.text("user", prompt.text()));
            messages.add(ChatMessage.toolCall(pending.thinking(), call));
            messages.add(ChatMessage.toolResult(selection.name(), JSON.writeValueAsString(result.modelContent())));
            BoundedRequestOutputStream output = new BoundedRequestOutputStream(MAX_REQUEST_BYTES);
            JSON.writeValue(output, new ChatRequest(model.modelName(), List.copyOf(messages),
                    List.of(), true, model.thinkingMode().enabled(), null,
                    new GenerateOptions(model.contextWindow(), model.responseTokenLimit())));
            return submitRequest(output.toByteArray(), model.thinkingMode(),
                    () -> { }, ignored -> { }, new ToolSelector(tools, List.of()));
        } catch (RequestLimitException exception) {
            return Result.failed(Status.LOCAL_LIMIT_REACHED);
        } catch (IOException exception) {
            if (isCausedByRequestLimit(exception)) return Result.failed(Status.LOCAL_LIMIT_REACHED);
            throw new IllegalStateException("Unable to encode validated tool continuation.", exception);
        }
    }

    /** Continues the exact approved search result; no tools are advertised. */
    public Result continueWithWebSearchResult(OllamaModelConfiguration model,
            OllamaPrompt prompt, Result pending, WebSearchResult result) {
        return continueWithToolResult(model, prompt, pending, result);
    }

    /** Continues one exact tool request with its bounded result and permits no further tool call. */
    public Result continueWithReadLocalFileResult(
            OllamaModelConfiguration model,
            OllamaPrompt prompt,
            Result toolCallResult,
            ReadLocalFileResult result) {
        return continueWithToolResult(model, prompt, toolCallResult, result);
    }

    /** Continues one exact HTTP GET request and permits no further tool call. */
    public Result continueWithHttpGetResult(
            OllamaModelConfiguration model,
            OllamaPrompt prompt,
            Result toolCallResult,
            HttpGetResult result) {
        return continueWithToolResult(model, prompt, toolCallResult, result);
    }

    /** Generates one streamed response and emits every validated answer chunk exactly once. */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt,
            Consumer<String> answerChunkConsumer) {
        return submit(model, ConversationHistory.empty(), prompt, () -> { }, answerChunkConsumer);
    }

    /**
     * Generates one streamed response with separate thinking progress and answer signals.
     *
     * <p>The thinking callback carries no generated text and runs at most once.</p>
     */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt,
            Runnable thinkingStarted, Consumer<String> answerChunkConsumer) {
        return submit(model, ConversationHistory.empty(), prompt,
                thinkingStarted, answerChunkConsumer);
    }

    /** Generates one streamed response using the supplied ordered conversation history. */
    public Result submit(OllamaModelConfiguration model, ConversationHistory history,
            OllamaPrompt prompt) {
        return submit(model, history, prompt, () -> { }, ignored -> { });
    }

    /**
     * Generates one streamed response from ordered history with separate progress signals.
     *
     * <p>The current prompt is always sent once as the final user message.</p>
     */
    public Result submit(OllamaModelConfiguration model, ConversationHistory history,
            OllamaPrompt prompt, Runnable thinkingStarted,
            Consumer<String> answerChunkConsumer) {
        return submit(model, history, prompt, thinkingStarted, answerChunkConsumer, List.of());
    }

    private Result submit(OllamaModelConfiguration model, ConversationHistory history,
            OllamaPrompt prompt, Runnable thinkingStarted,
            Consumer<String> answerChunkConsumer, List<String> allowedTools) {
        return submit(model, history, prompt, thinkingStarted, answerChunkConsumer,
                new ToolSelector(tools, allowedTools));
    }

    private Result submit(OllamaModelConfiguration model, ConversationHistory history,
            OllamaPrompt prompt, Runnable thinkingStarted,
            Consumer<String> answerChunkConsumer, ToolSelector selector) {
        return submit(model, history, prompt, thinkingStarted, answerChunkConsumer, selector, null);
    }

    private Result submit(OllamaModelConfiguration model, ConversationHistory history,
            OllamaPrompt prompt, Runnable thinkingStarted,
            Consumer<String> answerChunkConsumer, ToolSelector selector, JsonNode format) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(thinkingStarted, "thinkingStarted");
        Objects.requireNonNull(answerChunkConsumer, "answerChunkConsumer");

        byte[] requestBody = encodeRequest(model.modelName(), history, prompt,
                model.contextWindow(), model.thinkingMode(), model.responseTokenLimit(),
                selector, format);
        if (requestBody == null) {
            return Result.failed(Status.LOCAL_LIMIT_REACHED);
        }
        return submitRequest(requestBody, model.thinkingMode(), thinkingStarted,
                answerChunkConsumer, selector);
    }

    private Result submitRequest(byte[] requestBody, OllamaThinkingMode thinkingMode,
            Runnable thinkingStarted, Consumer<String> answerChunkConsumer,
            ToolSelector selector) {
        HttpRequest request = HttpRequest.newBuilder(chatEndpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/x-ndjson")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        long deadlineNanos = System.nanoTime() + requestTimeout.toNanos();
        HttpResponse<Flow.Publisher<List<ByteBuffer>>> response;
        try {
            response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofPublisher());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failed(Status.INTERRUPTED);
        } catch (HttpTimeoutException exception) {
            return Result.failed(Status.TOTAL_TIMEOUT);
        } catch (IOException | SecurityException exception) {
            return Result.failed(Status.UNAVAILABLE);
        }

        try (InputStream responseBody = new PublisherInputStream(
                response.body(), MAX_RESPONSE_BYTES, deadlineNanos, inactivityTimeout)) {
            if (response.statusCode() != 200) {
                return Result.failed(Status.REQUEST_FAILED);
            }
            if (!isNdjson(response)) {
                return Result.failed(Status.INVALID_RESPONSE);
            }
            return decodeStream(responseBody, thinkingMode, thinkingStarted,
                    answerChunkConsumer, selector);
        } catch (StreamInterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failed(Status.INTERRUPTED);
        } catch (StreamTimeoutException exception) {
            return Result.failed(exception.status());
        } catch (StreamLimitException exception) {
            return Result.failed(Status.LOCAL_LIMIT_REACHED);
        } catch (CharacterCodingException exception) {
            return Result.failed(Status.INVALID_RESPONSE);
        } catch (UnsafeStreamContentException exception) {
            return Result.failed(Status.INVALID_RESPONSE);
        } catch (IOException exception) {
            return Result.failed(Status.STREAM_FAILED);
        }
    }

    private static byte[] encodeRequest(String model, ConversationHistory history,
            OllamaPrompt prompt,
            int contextWindow, OllamaThinkingMode thinkingMode, int responseTokenLimit,
            ToolSelector selector, JsonNode format) {
        try {
            int instructionCount = prompt.systemInstruction().isEmpty() ? 0 : 1;
            List<ChatMessage> messages = new ArrayList<>(
                    history.messages().size() + instructionCount + 1);
            if (!prompt.systemInstruction().isEmpty()) {
                messages.add(ChatMessage.text("system", prompt.systemInstruction()));
            }
            for (ConversationMessage message : history.messages()) {
                messages.add(ChatMessage.text(chatRole(message.role()), message.content()));
            }
            messages.add(ChatMessage.text("user", prompt.text()));
            BoundedRequestOutputStream output =
                    new BoundedRequestOutputStream(MAX_REQUEST_BYTES);
            List<JsonNode> tools = selector.definitions();
            JSON.writeValue(output, new ChatRequest(
                    model,
                    List.copyOf(messages),
                    tools,
                    true,
                    thinkingMode.enabled(),
                    format,
                    new GenerateOptions(contextWindow, responseTokenLimit)));
            return output.toByteArray();
        } catch (RequestLimitException exception) {
            return null;
        } catch (IOException exception) {
            if (isCausedByRequestLimit(exception)) {
                return null;
            }
            throw new IllegalStateException(
                    "Unable to encode validated Ollama request.", exception);
        }
    }

    private static List<ToolResult<?>> validateAgentEvidence(List<ToolResult<?>> evidence) {
        List<ToolResult<?>> bounded = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        List<String> names = bounded.stream().map(ToolResult::toolName).toList();
        boolean valid = names.equals(List.of())
                || names.equals(List.of(ReadLocalFileToolContract.NAME))
                || names.equals(List.of(WebSearchToolContract.NAME))
                || names.equals(List.of(ReadLocalFileToolContract.NAME,
                        WebSearchToolContract.NAME));
        if (!valid) {
            throw new IllegalArgumentException("Agent evidence must match one bounded plan shape.");
        }
        return bounded;
    }

    private static boolean isCausedByRequestLimit(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestLimitException) {
                return true;
            }
        }
        return false;
    }

    private static String chatRole(ConversationRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private static Result decodeStream(InputStream body, OllamaThinkingMode thinkingMode,
            Runnable thinkingStarted, Consumer<String> answerChunkConsumer,
            ToolSelector selector) throws IOException {
        StringBuilder answer = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        boolean thinkingSignaled = false;
        boolean answerStarted = false;
        ToolSelection selection = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }
                StreamRecord record;
                try {
                    record = decodeRecord(line, selector);
                } catch (ToolSelectionException exception) {
                    return Result.selectionFailed(exception.reason());
                }
                if (record == null) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }
                appendGeneratedText(thinking, record.thinking(), MAX_THINKING_CODE_POINTS);
                appendGeneratedText(answer, record.response(), MAX_RESPONSE_CODE_POINTS);
                if (record.selection() != null) {
                    if (selection != null || answerStarted || !record.response().isEmpty()) {
                        return Result.failed(Status.INVALID_RESPONSE);
                    }
                    selection = record.selection();
                }
                if (!record.thinking().isEmpty()) {
                    if (thinkingMode == OllamaThinkingMode.OFF || answerStarted) {
                        return Result.failed(Status.INVALID_RESPONSE);
                    }
                    if (!thinkingSignaled) {
                        thinkingStarted.run();
                        thinkingSignaled = true;
                    }
                }
                if (record.done()) {
                    Result completed = complete(
                            record, thinkingMode, thinking, answer,
                            selection);
                    if (completed.successful() && !record.response().isEmpty()) {
                        answerChunkConsumer.accept(record.response());
                    }
                    if (reader.readLine() != null) {
                        return Result.failed(Status.INVALID_RESPONSE);
                    }
                    return completed;
                }
                if (!record.response().isEmpty()) {
                    if (selection != null) {
                        return Result.failed(Status.INVALID_RESPONSE);
                    }
                    answerStarted = true;
                    answerChunkConsumer.accept(record.response());
                }
            }
        }
        return Result.failed(Status.INVALID_RESPONSE);
    }

    private static StreamRecord decodeRecord(String line, ToolSelector selector) {
        try {
            JsonNode root = JSON.readTree(line);
            JsonNode message = root == null ? null : root.get("message");
            JsonNode role = message == null ? null : message.get("role");
            JsonNode response = message == null ? null : message.get("content");
            JsonNode thinking = message == null ? null : message.get("thinking");
            JsonNode toolCalls = message == null ? null : message.get("tool_calls");
            JsonNode done = root == null ? null : root.get("done");
            if (root == null || !root.isObject() || message == null || !message.isObject()
                    || role == null || !role.isTextual() || !"assistant".equals(role.textValue())
                    || response == null || !response.isTextual()
                    || (thinking != null && !thinking.isTextual())
                    || done == null || !done.isBoolean()
                    || (toolCalls != null && !toolCalls.isArray())) {
                return null;
            }
            ToolSelection selection = selector.select(toolCalls).orElse(null);
            return new StreamRecord(root, response.textValue(),
                    thinking == null ? "" : thinking.textValue(), done.booleanValue(),
                    selection);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Result complete(StreamRecord record, OllamaThinkingMode thinkingMode,
            StringBuilder thinking, StringBuilder answer,
            ToolSelection selection) {
        JsonNode reason = record.root().get("done_reason");
        CompletionMetrics metrics = decodeMetrics(record.root());
        if (reason == null || !reason.isTextual() || metrics == null) {
            return Result.failed(Status.INVALID_RESPONSE);
        }
        if ("length".equals(reason.textValue())) {
            return Result.completedFailure(
                    Status.TOKEN_LIMIT_REACHED, CompletionReason.LENGTH, metrics);
        }
        if (!"stop".equals(reason.textValue())) {
            return Result.failed(Status.INVALID_RESPONSE);
        }
        String retainedThinking = thinkingMode == OllamaThinkingMode.ON ? thinking.toString() : "";
        if (selection != null) {
            if (!answer.isEmpty()) return Result.failed(Status.INVALID_RESPONSE);
            return Result.selected(retainedThinking, selection, metrics);
        }
        if (answer.toString().isBlank()) {
            return Result.failed(Status.INVALID_RESPONSE);
        }
        return Result.success(retainedThinking, answer.toString(), metrics);
    }

    private static CompletionMetrics decodeMetrics(JsonNode root) {
        Long totalDuration = nonNegativeLong(root.get("total_duration"));
        Long promptTokens = nonNegativeLong(root.get("prompt_eval_count"));
        Long generatedTokens = nonNegativeLong(root.get("eval_count"));
        Long generatedDuration = nonNegativeLong(root.get("eval_duration"));
        return totalDuration == null || promptTokens == null || generatedTokens == null
                        || generatedDuration == null
                ? null
                : new CompletionMetrics(totalDuration, promptTokens,
                        generatedTokens, generatedDuration);
    }

    private static Long nonNegativeLong(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong()
                        && value.longValue() >= 0
                ? value.longValue() : null;
    }

    private static void appendGeneratedText(
            StringBuilder accumulated, String chunk, int maximumCodePoints) throws IOException {
        if (chunk == null
                || chunk.codePoints().anyMatch(OllamaPromptClient::isUnsafeOutputCharacter)) {
            throw new UnsafeStreamContentException();
        }
        accumulated.append(chunk);
        if (accumulated.codePointCount(0, accumulated.length()) > maximumCodePoints) {
            throw new StreamLimitException();
        }
    }

    private static boolean isNdjson(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").map(value -> {
            String normalized = value.toLowerCase(Locale.ROOT);
            return normalized.startsWith("application/x-ndjson")
                    || normalized.startsWith("application/json");
        }).orElse(false);
    }

    private static boolean isUnsafeOutputCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t';
    }

    private static URI requireLoopbackHttpEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "chatEndpoint");
        String host = endpoint.getHost();
        boolean loopbackHost = "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || !loopbackHost) {
            throw new IllegalArgumentException(
                    "Ollama chat endpoint must use local loopback HTTP.");
        }
        return endpoint;
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "requestTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Ollama request timeout must be positive.");
        }
        return timeout;
    }

    private record ChatRequest(String model, List<ChatMessage> messages,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<JsonNode> tools,
            boolean stream, boolean think,
            @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode format,
            GenerateOptions options) { }

    private record ChatMessage(
            String role,
            String content,
            @JsonProperty("tool_calls")
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<JsonNode> toolCalls,
            @JsonProperty("tool_name")
            @JsonInclude(JsonInclude.Include.NON_NULL) String toolName,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) String thinking) {

        private static ChatMessage text(String role, String content) {
            return new ChatMessage(role, content, List.of(), null, "");
        }

        private static ChatMessage toolCall(String thinking, JsonNode toolCall) {
            return new ChatMessage("assistant", "", List.of(toolCall), null, thinking);
        }

        private static ChatMessage toolResult(String toolName, String content) {
            return new ChatMessage(
                    "tool", content, List.of(), toolName, "");
        }
    }

    private record GenerateOptions(@JsonProperty("num_ctx") int contextWindow,
            @JsonProperty("num_predict") int responseTokenLimit) { }

    private record StreamRecord(JsonNode root, String response, String thinking, boolean done,
            ToolSelection selection) { }

    /** Validated metrics from Ollama's terminal streaming record. */
    public record CompletionMetrics(long totalDurationNanos, long promptTokenCount,
            long generatedTokenCount, long generatedDurationNanos) {
        public CompletionMetrics {
            if (totalDurationNanos < 0 || promptTokenCount < 0
                    || generatedTokenCount < 0 || generatedDurationNanos < 0) {
                throw new IllegalArgumentException(
                        "Ollama completion metrics must be non-negative.");
            }
        }

        static CompletionMetrics unavailable() {
            return new CompletionMetrics(0, 0, 0, 0);
        }
    }

    /** Completion categories currently accepted from Ollama's terminal record. */
    public enum CompletionReason {
        NONE,
        STOP,
        LENGTH
    }

    /** Safe provider result. Typed constructors/accessors retain compatibility with existing commands. */
    public record Result(Status status, String thinking, String response,
            CompletionReason completionReason, CompletionMetrics metrics,
            Optional<ToolSelection> selection, Optional<ToolSelectionException.Reason> selectionFailure) {
        public Result {
            Objects.requireNonNull(status);
            Objects.requireNonNull(thinking);
            Objects.requireNonNull(response);
            Objects.requireNonNull(completionReason);
            Objects.requireNonNull(metrics);
            Objects.requireNonNull(selection);
            Objects.requireNonNull(selectionFailure);
            if (status == Status.SUCCESS
                    && ((response.isBlank() ? 0 : 1) + (selection.isPresent() ? 1 : 0) != 1)) {
                throw new IllegalArgumentException("Success requires exactly one answer or selection.");
            }
            if (status != Status.SUCCESS && (!thinking.isEmpty() || !response.isEmpty() || selection.isPresent())) {
                throw new IllegalArgumentException("Failed prompt result must not contain generated data.");
            }
            if ((status == Status.SUCCESS && completionReason != CompletionReason.STOP)
                    || (status == Status.TOKEN_LIMIT_REACHED && completionReason != CompletionReason.LENGTH)
                    || (status != Status.SUCCESS && status != Status.TOKEN_LIMIT_REACHED
                            && completionReason != CompletionReason.NONE)
                    || (selectionFailure.isPresent() && status != Status.INVALID_RESPONSE)) {
                throw new IllegalArgumentException("Prompt result status and completion reason must agree.");
            }
        }

        public Result(Status status, String thinking, String response,
                Optional<ReadLocalFileRequest> file, Optional<HttpGetRequest> http,
                Optional<WebSearchRequest> search, CompletionReason reason, CompletionMetrics metrics) {
            this(status, thinking, response, reason, metrics, legacySelection(file, http, search), Optional.empty());
        }
        public Result(Status status, String thinking, String response,
                Optional<ReadLocalFileRequest> file, Optional<HttpGetRequest> http,
                CompletionReason reason, CompletionMetrics metrics) {
            this(status, thinking, response, file, http, Optional.empty(), reason, metrics);
        }
        public Result(Status status, String thinking, String response,
                Optional<ReadLocalFileRequest> file, CompletionReason reason, CompletionMetrics metrics) {
            this(status, thinking, response, file, Optional.empty(), reason, metrics);
        }
        public Result(Status status, String thinking, String response) {
            this(status, thinking, response,
                    status == Status.SUCCESS ? CompletionReason.STOP
                            : status == Status.TOKEN_LIMIT_REACHED ? CompletionReason.LENGTH : CompletionReason.NONE,
                    CompletionMetrics.unavailable(), Optional.empty(), Optional.empty());
        }

        private static Optional<ToolSelection> legacySelection(Optional<ReadLocalFileRequest> file,
                Optional<HttpGetRequest> http, Optional<WebSearchRequest> search) {
            int count = (file.isPresent() ? 1 : 0) + (http.isPresent() ? 1 : 0) + (search.isPresent() ? 1 : 0);
            if (count > 1) throw new IllegalArgumentException("Expected at most one tool request.");
            if (count == 0) return Optional.empty();
            // Only old typed construction needs this bridge; streaming and dispatch are registry-based.
            String name;
            var arguments = JSON.createObjectNode();
            if (file.isPresent()) {
                name = ReadLocalFileToolContract.NAME;
                arguments.put(ReadLocalFileToolContract.PATH_ARGUMENT, file.orElseThrow().path());
            } else if (http.isPresent()) {
                name = HttpGetToolContract.NAME;
                arguments.put(HttpGetToolContract.URL_ARGUMENT, http.orElseThrow().url());
            } else {
                name = WebSearchToolContract.NAME;
                arguments.put("query", search.orElseThrow().query());
            }
            var function = JSON.createObjectNode().put("name", name).set("arguments", arguments);
            var calls = JSON.createArrayNode().add(JSON.createObjectNode().set("function", function));
            return new ToolSelector(StandardTools.create(), List.of(name)).select(calls);
        }

        static Result success(String thinking, String response, CompletionMetrics metrics) {
            return new Result(Status.SUCCESS, thinking, response, CompletionReason.STOP, metrics,
                    Optional.empty(), Optional.empty());
        }
        static Result selected(String thinking, ToolSelection selection, CompletionMetrics metrics) {
            return new Result(Status.SUCCESS, thinking, "", CompletionReason.STOP, metrics,
                    Optional.of(selection), Optional.empty());
        }
        static Result completedFailure(Status status, CompletionReason reason, CompletionMetrics metrics) {
            return new Result(status, "", "", reason, metrics, Optional.empty(), Optional.empty());
        }
        static Result failed(Status status) {
            return completedFailure(status, CompletionReason.NONE, CompletionMetrics.unavailable());
        }
        static Result selectionFailed(ToolSelectionException.Reason reason) {
            return new Result(Status.INVALID_RESPONSE, "", "", CompletionReason.NONE,
                    CompletionMetrics.unavailable(), Optional.empty(), Optional.of(reason));
        }
        public Optional<ReadLocalFileRequest> toolRequest() {
            return selection.flatMap(value -> value.request(ReadLocalFileRequest.class));
        }
        public Optional<HttpGetRequest> httpGetRequest() {
            return selection.flatMap(value -> value.request(HttpGetRequest.class));
        }
        public Optional<WebSearchRequest> webSearchRequest() {
            return selection.flatMap(value -> value.request(WebSearchRequest.class));
        }
        public boolean successful() { return status == Status.SUCCESS; }
        public boolean toolRequested() { return selection.isPresent(); }
        public boolean httpGetRequested() { return httpGetRequest().isPresent(); }
        @Override public String toString() {
            return "Result[status=" + status + ", successful=" + successful()
                    + ", toolRequested=" + toolRequested() + "]";
        }
    }

    /** Safe outcome categories for one local Ollama prompt lifecycle. */
    public enum Status { SUCCESS, TOKEN_LIMIT_REACHED, UNAVAILABLE, REQUEST_FAILED,
        INVALID_RESPONSE, STREAM_FAILED, LOCAL_LIMIT_REACHED, TOTAL_TIMEOUT,
        INACTIVITY_TIMEOUT, INTERRUPTED }

    private static final class PublisherInputStream extends InputStream {
        private final PublisherSubscriber subscriber;
        private final long deadlineNanos;
        private final long inactivityTimeoutNanos;
        private byte[] current = new byte[0];
        private int currentOffset;
        private boolean complete;

        private PublisherInputStream(Flow.Publisher<List<ByteBuffer>> publisher,
                long maximumBytes, long deadlineNanos, Duration inactivityTimeout) {
            subscriber = new PublisherSubscriber(maximumBytes);
            this.deadlineNanos = deadlineNanos;
            inactivityTimeoutNanos = inactivityTimeout.toNanos();
            Objects.requireNonNull(publisher, "publisher").subscribe(subscriber);
        }

        @Override
        public int read() throws IOException {
            byte[] singleByte = new byte[1];
            int count = read(singleByte, 0, 1);
            return count == -1 ? -1 : Byte.toUnsignedInt(singleByte[0]);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) return 0;
            while (currentOffset >= current.length) {
                if (complete) return -1;
                receiveNext();
            }
            int count = Math.min(length, current.length - currentOffset);
            System.arraycopy(current, currentOffset, buffer, offset, count);
            currentOffset += count;
            if (currentOffset >= current.length) subscriber.requestNext();
            return count;
        }

        private void receiveNext() throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                subscriber.cancel();
                throw new StreamInterruptedException();
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                subscriber.cancel();
                throw new StreamTimeoutException(Status.TOTAL_TIMEOUT);
            }
            boolean totalDeadlineFirst = remainingNanos <= inactivityTimeoutNanos;
            StreamEvent event;
            try {
                event = subscriber.poll(Math.min(remainingNanos, inactivityTimeoutNanos));
            } catch (InterruptedException exception) {
                subscriber.cancel();
                Thread.currentThread().interrupt();
                throw new StreamInterruptedException();
            }
            if (event == null) {
                subscriber.cancel();
                throw new StreamTimeoutException(totalDeadlineFirst
                        ? Status.TOTAL_TIMEOUT : Status.INACTIVITY_TIMEOUT);
            }
            switch (event.type()) {
                case DATA -> {
                    current = event.data();
                    currentOffset = 0;
                }
                case COMPLETE -> complete = true;
                case LIMIT -> throw new StreamLimitException();
                case TIMEOUT -> throw new StreamTimeoutException(Status.TOTAL_TIMEOUT);
                case FAILED -> throw new IOException("Ollama response stream failed.");
            }
        }

        @Override
        public void close() {
            subscriber.cancel();
        }
    }

    private static final class PublisherSubscriber
            implements Flow.Subscriber<List<ByteBuffer>> {
        private final BlockingQueue<StreamEvent> events = new ArrayBlockingQueue<>(2);
        private final long maximumBytes;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private Flow.Subscription subscription;
        private long bytesReceived;

        private PublisherSubscriber(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription candidate) {
            Objects.requireNonNull(candidate, "subscription");
            if (subscription != null || terminal.get()) {
                candidate.cancel();
                return;
            }
            subscription = candidate;
            candidate.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (terminal.get()) return;
            int size = 0;
            try {
                for (ByteBuffer buffer : Objects.requireNonNull(buffers, "buffers")) {
                    size = Math.addExact(size,
                            Objects.requireNonNull(buffer, "buffer").remaining());
                }
            } catch (ArithmeticException exception) {
                limit();
                return;
            } catch (NullPointerException exception) {
                fail();
                return;
            }
            if (size == 0) {
                requestNext();
                return;
            }
            if (bytesReceived > maximumBytes - size) {
                limit();
                return;
            }
            byte[] data = new byte[size];
            int offset = 0;
            for (ByteBuffer buffer : buffers) {
                ByteBuffer readable = buffer.asReadOnlyBuffer();
                int count = readable.remaining();
                readable.get(data, offset, count);
                offset += count;
            }
            bytesReceived += size;
            if (!signal(new StreamEvent(StreamEventType.DATA, data))) fail();
        }

        @Override
        public void onError(Throwable failure) {
            if (containsTimeout(failure)) {
                if (terminal.compareAndSet(false, true)) {
                    signal(new StreamEvent(StreamEventType.TIMEOUT, new byte[0]));
                }
                cancelSubscription();
            } else {
                fail();
            }
        }

        @Override
        public void onComplete() {
            if (terminal.compareAndSet(false, true)) {
                signal(new StreamEvent(StreamEventType.COMPLETE, new byte[0]));
            }
        }

        private StreamEvent poll(long timeoutNanos) throws InterruptedException {
            return events.poll(timeoutNanos, TimeUnit.NANOSECONDS);
        }

        private synchronized void requestNext() {
            if (subscription != null && !terminal.get()) subscription.request(1);
        }

        private void cancel() {
            terminal.set(true);
            cancelSubscription();
        }

        private synchronized void cancelSubscription() {
            if (subscription != null) subscription.cancel();
        }

        private void fail() {
            if (terminal.compareAndSet(false, true)) {
                signal(new StreamEvent(StreamEventType.FAILED, new byte[0]));
            }
            cancelSubscription();
        }

        private void limit() {
            if (terminal.compareAndSet(false, true)) {
                signal(new StreamEvent(StreamEventType.LIMIT, new byte[0]));
            }
            cancelSubscription();
        }

        private boolean signal(StreamEvent event) {
            return events.offer(event);
        }

        private static boolean containsTimeout(Throwable failure) {
            Throwable current = failure;
            while (current != null) {
                if (current instanceof HttpTimeoutException) return true;
                current = current.getCause();
            }
            return false;
        }
    }

    private static final class BoundedRequestOutputStream extends OutputStream {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int maximumBytes;

        private BoundedRequestOutputStream(int maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) {
            requireCapacity(1);
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            requireCapacity(length);
            output.write(bytes, offset, length);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }

        private void requireCapacity(int additionalBytes) {
            if (additionalBytes > maximumBytes - output.size()) {
                throw new RequestLimitException();
            }
        }
    }

    private static final class RequestLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record StreamEvent(StreamEventType type, byte[] data) { }

    private enum StreamEventType { DATA, COMPLETE, LIMIT, TIMEOUT, FAILED }

    private static final class StreamInterruptedException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class StreamTimeoutException extends IOException {
        private static final long serialVersionUID = 1L;

        private final Status status;

        private StreamTimeoutException(Status status) {
            this.status = Objects.requireNonNull(status, "status");
        }

        private Status status() {
            return status;
        }
    }

    private static final class StreamLimitException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class UnsafeStreamContentException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
