package io.kaos.ai.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Submits one bounded streaming generation request to local Ollama. */
public final class OllamaPromptClient {
    public static final URI LOCAL_GENERATE_ENDPOINT =
            URI.create("http://127.0.0.1:11434/api/generate");
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    public static final int MAX_RESPONSE_CODE_POINTS = 65_536;
    public static final int MAX_THINKING_CODE_POINTS = 65_536;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI generateEndpoint;
    private final Duration requestTimeout;

    /** Creates a client restricted to the fixed local Ollama generate endpoint. */
    public OllamaPromptClient() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                LOCAL_GENERATE_ENDPOINT, REQUEST_TIMEOUT);
    }

    OllamaPromptClient(HttpClient httpClient, URI generateEndpoint, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.generateEndpoint = requireLoopbackHttpEndpoint(generateEndpoint);
        this.requestTimeout = requirePositiveTimeout(requestTimeout);
    }

    /** Generates one streamed response without exposing progressive chunks to the caller. */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt) {
        return submit(model, prompt, () -> { }, ignored -> { });
    }

    /** Generates one streamed response and emits every validated answer chunk exactly once. */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt,
            Consumer<String> answerChunkConsumer) {
        return submit(model, prompt, () -> { }, answerChunkConsumer);
    }

    /**
     * Generates one streamed response with separate thinking progress and answer signals.
     *
     * <p>The thinking callback carries no generated text and runs at most once.</p>
     */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt,
            Runnable thinkingStarted, Consumer<String> answerChunkConsumer) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(thinkingStarted, "thinkingStarted");
        Objects.requireNonNull(answerChunkConsumer, "answerChunkConsumer");

        byte[] requestBody = encodeRequest(model.modelName(), prompt.text(), model.contextWindow(),
                model.thinkingMode(), model.responseTokenLimit());
        HttpRequest request = HttpRequest.newBuilder(generateEndpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/x-ndjson")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream responseBody = response.body()) {
                if (response.statusCode() != 200) {
                    return Result.failed(Status.REQUEST_FAILED);
                }
                if (!isNdjson(response)) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }
                return decodeStream(new LimitedInputStream(responseBody, MAX_RESPONSE_BYTES),
                        model.thinkingMode(), thinkingStarted, answerChunkConsumer);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failed(Status.INTERRUPTED);
        } catch (HttpTimeoutException exception) {
            return Result.failed(Status.TIMED_OUT);
        } catch (CharacterCodingException exception) {
            return Result.failed(Status.INVALID_RESPONSE);
        } catch (ResponseLimitException exception) {
            return Result.failed(Status.INVALID_RESPONSE);
        } catch (IOException | SecurityException exception) {
            return Result.failed(Status.UNAVAILABLE);
        }
    }

    private static byte[] encodeRequest(String model, String prompt, int contextWindow,
            OllamaThinkingMode thinkingMode, int responseTokenLimit) {
        try {
            return JSON.writeValueAsBytes(new GenerateRequest(
                    model,
                    prompt,
                    true,
                    thinkingMode.enabled(),
                    new GenerateOptions(contextWindow, responseTokenLimit)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Unable to encode validated Ollama request.", exception);
        }
    }

    private static Result decodeStream(InputStream body, OllamaThinkingMode thinkingMode,
            Runnable thinkingStarted, Consumer<String> answerChunkConsumer) throws IOException {
        StringBuilder answer = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        boolean thinkingSignaled = false;
        boolean answerStarted = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }
                StreamRecord record = decodeRecord(line);
                if (record == null
                        || !appendGeneratedText(thinking, record.thinking(),
                                MAX_THINKING_CODE_POINTS)
                        || !appendGeneratedText(answer, record.response(),
                                MAX_RESPONSE_CODE_POINTS)) {
                    return Result.failed(Status.INVALID_RESPONSE);
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
                    Result completed = complete(record, thinkingMode, thinking, answer);
                    if (completed.successful() && !record.response().isEmpty()) {
                        answerChunkConsumer.accept(record.response());
                    }
                    if (reader.readLine() != null) {
                        return Result.failed(Status.INVALID_RESPONSE);
                    }
                    return completed;
                }
                if (!record.response().isEmpty()) {
                    answerStarted = true;
                    answerChunkConsumer.accept(record.response());
                }
            }
        }
        return Result.failed(Status.INVALID_RESPONSE);
    }

    private static StreamRecord decodeRecord(String line) {
        try {
            JsonNode root = JSON.readTree(line);
            JsonNode response = root == null ? null : root.get("response");
            JsonNode thinking = root == null ? null : root.get("thinking");
            JsonNode done = root == null ? null : root.get("done");
            if (root == null || !root.isObject() || response == null || !response.isTextual()
                    || (thinking != null && !thinking.isTextual())
                    || done == null || !done.isBoolean()) {
                return null;
            }
            return new StreamRecord(root, response.textValue(),
                    thinking == null ? "" : thinking.textValue(), done.booleanValue());
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Result complete(StreamRecord record, OllamaThinkingMode thinkingMode,
            StringBuilder thinking, StringBuilder answer) {
        JsonNode reason = record.root().get("done_reason");
        CompletionMetrics metrics = decodeMetrics(record.root());
        if (reason == null || !reason.isTextual() || metrics == null) {
            return Result.failed(Status.INVALID_RESPONSE);
        }
        if ("length".equals(reason.textValue())) {
            return Result.completedFailure(
                    Status.TOKEN_LIMIT_REACHED, CompletionReason.LENGTH, metrics);
        }
        if (!"stop".equals(reason.textValue()) || answer.toString().isBlank()) {
            return Result.failed(Status.INVALID_RESPONSE);
        }
        String retainedThinking = thinkingMode == OllamaThinkingMode.ON ? thinking.toString() : "";
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

    private static boolean appendGeneratedText(
            StringBuilder accumulated, String chunk, int maximumCodePoints) {
        if (chunk == null
                || chunk.codePoints().anyMatch(OllamaPromptClient::isUnsafeOutputCharacter)) {
            return false;
        }
        accumulated.append(chunk);
        return accumulated.codePointCount(0, accumulated.length()) <= maximumCodePoints;
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
        Objects.requireNonNull(endpoint, "generateEndpoint");
        String host = endpoint.getHost();
        boolean loopbackHost = "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || !loopbackHost) {
            throw new IllegalArgumentException(
                    "Ollama generate endpoint must use local loopback HTTP.");
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

    private record GenerateRequest(String model, String prompt, boolean stream, boolean think,
            GenerateOptions options) { }

    private record GenerateOptions(@JsonProperty("num_ctx") int contextWindow,
            @JsonProperty("num_predict") int responseTokenLimit) { }

    private record StreamRecord(JsonNode root, String response, String thinking, boolean done) { }

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

    /** Safe outcome of one streaming prompt request. */
    public record Result(
            Status status,
            String thinking,
            String response,
            CompletionReason completionReason,
            CompletionMetrics metrics) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(thinking, "thinking");
            Objects.requireNonNull(response, "response");
            Objects.requireNonNull(completionReason, "completionReason");
            Objects.requireNonNull(metrics, "metrics");
            if (status == Status.SUCCESS && response.isBlank()) {
                throw new IllegalArgumentException("Successful prompt result requires a response.");
            }
            if (status != Status.SUCCESS && (!thinking.isEmpty() || !response.isEmpty())) {
                throw new IllegalArgumentException(
                        "Failed prompt result must not contain generated data.");
            }
            if ((status == Status.SUCCESS && completionReason != CompletionReason.STOP)
                    || (status == Status.TOKEN_LIMIT_REACHED
                            && completionReason != CompletionReason.LENGTH)
                    || (status != Status.SUCCESS
                            && status != Status.TOKEN_LIMIT_REACHED
                            && completionReason != CompletionReason.NONE)) {
                throw new IllegalArgumentException(
                        "Prompt result status and completion reason must agree.");
            }
        }

        public Result(Status status, String thinking, String response) {
            this(
                    status,
                    thinking,
                    response,
                    status == Status.SUCCESS
                            ? CompletionReason.STOP
                            : status == Status.TOKEN_LIMIT_REACHED
                                    ? CompletionReason.LENGTH
                                    : CompletionReason.NONE,
                    CompletionMetrics.unavailable());
        }

        static Result success(String thinking, String response, CompletionMetrics metrics) {
            return new Result(
                    Status.SUCCESS, thinking, response, CompletionReason.STOP, metrics);
        }

        static Result completedFailure(
                Status status, CompletionReason completionReason, CompletionMetrics metrics) {
            return new Result(status, "", "", completionReason, metrics);
        }

        static Result failed(Status status) {
            return new Result(
                    status,
                    "",
                    "",
                    CompletionReason.NONE,
                    CompletionMetrics.unavailable());
        }

        public boolean successful() { return status == Status.SUCCESS; }

        @Override
        public String toString() {
            return "Result[status=" + status + ", successful=" + successful() + "]";
        }
    }

    /** Minimal categories that later AI failure-handling work may refine. */
    public enum Status { SUCCESS, TOKEN_LIMIT_REACHED, UNAVAILABLE, REQUEST_FAILED,
        INVALID_RESPONSE, TIMED_OUT, INTERRUPTED }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytesRead;

        private LimitedInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1) recordRead(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) recordRead(count);
            return count;
        }

        private void recordRead(int count) throws ResponseLimitException {
            bytesRead += count;
            if (bytesRead > maximumBytes) throw new ResponseLimitException();
        }
    }

    private static final class ResponseLimitException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
