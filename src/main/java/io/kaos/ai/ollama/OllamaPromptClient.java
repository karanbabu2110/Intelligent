package io.kaos.ai.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Submits one bounded, non-streamed generation request to local Ollama. */
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
        this(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                LOCAL_GENERATE_ENDPOINT,
                REQUEST_TIMEOUT);
    }

    OllamaPromptClient(HttpClient httpClient, URI generateEndpoint, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.generateEndpoint = requireLoopbackHttpEndpoint(generateEndpoint);
        this.requestTimeout = requirePositiveTimeout(requestTimeout);
    }

    /**
     * Generates one complete response with Ollama streaming explicitly disabled.
     *
     * @return a bounded result containing no raw failure body or exception detail
     */
    public Result submit(OllamaModelConfiguration model, OllamaPrompt prompt) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(prompt, "prompt");

        byte[] requestBody = encodeRequest(
                model.modelName(),
                prompt.text(),
                model.contextWindow(),
                model.thinkingMode(),
                model.responseTokenLimit());
        HttpRequest request = HttpRequest.newBuilder(generateEndpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();

        try {
            HttpResponse<InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream responseBody = response.body()) {
                if (response.statusCode() != 200) {
                    return Result.failed(Status.REQUEST_FAILED);
                }
                if (!isJson(response)) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }

                byte[] boundedBody = responseBody.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (boundedBody.length > MAX_RESPONSE_BYTES) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }

                Result completed =
                        decodeCompletedResponse(boundedBody, model.thinkingMode());
                if (completed == null) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }
                return completed;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failed(Status.INTERRUPTED);
        } catch (HttpTimeoutException exception) {
            return Result.failed(Status.TIMED_OUT);
        } catch (IOException | SecurityException exception) {
            return Result.failed(Status.UNAVAILABLE);
        }
    }

    private static byte[] encodeRequest(
            String model,
            String prompt,
            int contextWindow,
            OllamaThinkingMode thinkingMode,
            int responseTokenLimit) {
        try {
            return JSON.writeValueAsBytes(new GenerateRequest(
                    model,
                    prompt,
                    false,
                    thinkingMode.enabled(),
                    new GenerateOptions(contextWindow, responseTokenLimit)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to encode validated Ollama request.", exception);
        }
    }

    private static Result decodeCompletedResponse(
            byte[] body, OllamaThinkingMode thinkingMode) {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode response = root == null ? null : root.get("response");
            JsonNode thinking = root == null ? null : root.get("thinking");
            JsonNode done = root == null ? null : root.get("done");
            JsonNode doneReason = root == null ? null : root.get("done_reason");
            if (root == null
                    || !root.isObject()
                    || response == null
                    || !response.isTextual()
                    || (thinking != null && !thinking.isTextual())
                    || done == null
                    || !done.isBoolean()
                    || !done.booleanValue()
                    || doneReason == null
                    || !doneReason.isTextual()) {
                return null;
            }

            String responseText = response.textValue();
            String thinkingText = thinking == null ? "" : thinking.textValue();
            String completionReason = doneReason.textValue();
            boolean tokenLimitReached = "length".equals(completionReason);
            if (!("stop".equals(completionReason) || tokenLimitReached)
                    || !isValidGeneratedText(
                            responseText, MAX_RESPONSE_CODE_POINTS, tokenLimitReached)
                    || !isValidGeneratedText(thinkingText, MAX_THINKING_CODE_POINTS, true)) {
                return null;
            }
            if (tokenLimitReached) {
                return Result.failed(Status.TOKEN_LIMIT_REACHED);
            }
            if (thinkingMode == OllamaThinkingMode.OFF) {
                thinkingText = "";
            }
            return Result.success(thinkingText, responseText);
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean isValidGeneratedText(
            String text, int maximumCodePoints, boolean blankAllowed) {
        return text != null
                && (blankAllowed || !text.isBlank())
                && text.codePointCount(0, text.length()) <= maximumCodePoints
                && text.codePoints().noneMatch(OllamaPromptClient::isUnsafeOutputCharacter);
    }

    private static boolean isJson(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("application/json"))
                .orElse(false);
    }

    private static boolean isUnsafeOutputCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }

    private static URI requireLoopbackHttpEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "generateEndpoint");
        String host = endpoint.getHost();
        boolean loopbackHost = "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host);
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || !loopbackHost) {
            throw new IllegalArgumentException("Ollama generate endpoint must use local loopback HTTP.");
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

    private record GenerateRequest(
            String model,
            String prompt,
            boolean stream,
            boolean think,
            GenerateOptions options) {
    }

    private record GenerateOptions(
            @JsonProperty("num_ctx") int contextWindow,
            @JsonProperty("num_predict") int responseTokenLimit) {
    }

    /** Safe outcome of one non-streamed prompt request. */
    public record Result(Status status, String thinking, String response) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(thinking, "thinking");
            Objects.requireNonNull(response, "response");
            if (status == Status.SUCCESS && response.isBlank()) {
                throw new IllegalArgumentException("Successful prompt result requires a response.");
            }
            if (status != Status.SUCCESS && (!thinking.isEmpty() || !response.isEmpty())) {
                throw new IllegalArgumentException(
                        "Failed prompt result must not contain generated data.");
            }
        }

        static Result success(String thinking, String response) {
            return new Result(Status.SUCCESS, thinking, response);
        }

        static Result failed(Status status) {
            return new Result(status, "", "");
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        @Override
        public String toString() {
            return "Result[status=" + status + ", successful=" + successful() + "]";
        }
    }

    /** Minimal categories that later AI failure-handling work may refine. */
    public enum Status {
        SUCCESS,
        TOKEN_LIMIT_REACHED,
        UNAVAILABLE,
        REQUEST_FAILED,
        INVALID_RESPONSE,
        TIMED_OUT,
        INTERRUPTED
    }
}
