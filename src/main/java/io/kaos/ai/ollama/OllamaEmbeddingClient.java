package io.kaos.ai.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.EmbeddedChunk;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Generates bounded embeddings through the fixed loopback Ollama endpoint. */
public final class OllamaEmbeddingClient {
    public static final URI LOCAL_EMBED_ENDPOINT = URI.create("http://127.0.0.1:11434/api/embed");
    public static final int MAX_RESPONSE_BYTES = 131_072;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration TOTAL_TIMEOUT = Duration.ofMinutes(5);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI endpoint;
    private final Duration totalTimeout;

    public OllamaEmbeddingClient() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                LOCAL_EMBED_ENDPOINT, TOTAL_TIMEOUT);
    }

    OllamaEmbeddingClient(HttpClient httpClient, URI endpoint, Duration totalTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = requireLoopback(endpoint);
        this.totalTimeout = Objects.requireNonNull(totalTimeout, "totalTimeout");
        if (totalTimeout.isZero() || totalTimeout.isNegative()) {
            throw new IllegalArgumentException("totalTimeout must be positive");
        }
    }

    /** Embeds every chunk in order within one shared total deadline. */
    public Result embed(OllamaEmbeddingConfiguration configuration, List<DocumentChunk> chunks) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(chunks, "chunks");
        if (chunks.isEmpty()) return Result.failed(Status.INVALID_RESPONSE);
        long deadline = System.nanoTime() + totalTimeout.toNanos();
        List<EmbeddedChunk> embedded = new ArrayList<>(chunks.size());
        Integer dimensions = null;
        for (DocumentChunk chunk : chunks) {
            Objects.requireNonNull(chunk, "chunk");
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return Result.failed(Status.TIMEOUT);
            byte[] body;
            try {
                body = JSON.writeValueAsBytes(Map.of(
                        "model", configuration.modelName(),
                        "input", chunk.content(),
                        "truncate", false));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to encode validated embedding request.", exception);
            }
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofNanos(remaining))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Result.failed(Status.INTERRUPTED);
            } catch (HttpTimeoutException exception) {
                return Result.failed(Status.TIMEOUT);
            } catch (IOException | SecurityException exception) {
                return Result.failed(Status.UNAVAILABLE);
            }
            try (InputStream stream = response.body()) {
                if (response.statusCode() != 200) return Result.failed(Status.REQUEST_FAILED);
                if (!isJson(response)) return Result.failed(Status.INVALID_RESPONSE);
                byte[] responseBytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (responseBytes.length > MAX_RESPONSE_BYTES) return Result.failed(Status.LOCAL_LIMIT_REACHED);
                double[] vector = decode(configuration.modelName(), responseBytes);
                if (vector == null) return Result.failed(Status.INVALID_RESPONSE);
                if (dimensions != null && dimensions != vector.length) {
                    return Result.failed(Status.INVALID_RESPONSE);
                }
                dimensions = vector.length;
                embedded.add(new EmbeddedChunk(chunk, vector));
            } catch (IOException exception) {
                return Result.failed(Status.UNAVAILABLE);
            }
        }
        return Result.success(embedded);
    }

    private static double[] decode(String expectedModel, byte[] body) {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode model = root == null ? null : root.get("model");
            JsonNode embeddings = root == null ? null : root.get("embeddings");
            if (root == null || !root.isObject() || model == null || !model.isTextual()
                    || !expectedModel.equals(model.textValue()) || embeddings == null
                    || !embeddings.isArray() || embeddings.size() != 1
                    || !embeddings.get(0).isArray() || embeddings.get(0).isEmpty()
                    || embeddings.get(0).size() > EmbeddedChunk.MAX_DIMENSIONS) return null;
            double[] vector = new double[embeddings.get(0).size()];
            for (int index = 0; index < vector.length; index++) {
                JsonNode value = embeddings.get(0).get(index);
                if (!value.isNumber() || !Double.isFinite(value.doubleValue())) return null;
                vector[index] = value.doubleValue();
            }
            return vector;
        } catch (IOException exception) {
            return null;
        }
    }

    private static boolean isJson(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("application/json"))
                .orElse(false);
    }

    private static URI requireLoopback(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || !("127.0.0.1".equals(endpoint.getHost()) || "localhost".equalsIgnoreCase(endpoint.getHost()))) {
            throw new IllegalArgumentException("embedding endpoint must use loopback HTTP");
        }
        return endpoint;
    }

    public enum Status { SUCCESS, UNAVAILABLE, REQUEST_FAILED, INVALID_RESPONSE, LOCAL_LIMIT_REACHED, TIMEOUT, INTERRUPTED }

    public record Result(Status status, List<EmbeddedChunk> embeddedChunks) {
        public Result {
            Objects.requireNonNull(status, "status");
            embeddedChunks = List.copyOf(embeddedChunks);
        }
        static Result success(List<EmbeddedChunk> chunks) { return new Result(Status.SUCCESS, chunks); }
        static Result failed(Status status) { return new Result(status, List.of()); }
        public boolean successful() { return status == Status.SUCCESS; }
    }
}
