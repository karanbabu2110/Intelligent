package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.kaos.knowledge.DocumentChunk;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class OllamaEmbeddingClientTest {
    @Test
    void generatesOneOrderedBoundedVectorPerChunkWithoutTruncation() throws Exception {
        List<String> requests = new ArrayList<>();
        try (LocalEmbeddingServer server = new LocalEmbeddingServer((exchange, sequence) -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"model\":\"embeddinggemma\",\"embeddings\":[["
                    + sequence + ",0.5]]}", "application/json");
        })) {
            List<DocumentChunk> chunks = List.of(
                    new DocumentChunk("notes.txt", 0, 0, 3, "one"),
                    new DocumentChunk("notes.txt", 1, 3, 6, "two"));

            OllamaEmbeddingClient.Result result = client(server.endpoint()).embed(
                    new OllamaEmbeddingConfiguration("embeddinggemma"), chunks);

            assertTrue(result.successful());
            assertEquals(2, result.embeddedChunks().size());
            assertEquals(0.0, result.embeddedChunks().get(0).vector()[0]);
            assertEquals(1.0, result.embeddedChunks().get(1).vector()[0]);
            assertTrue(requests.get(0).contains("\"input\":\"one\""));
            assertTrue(requests.get(1).contains("\"input\":\"two\""));
            assertTrue(requests.stream().allMatch(body -> body.contains("\"truncate\":false")));
        }
    }

    @Test
    void rejectsDimensionChangesAndDoesNotExposeProviderContent() throws Exception {
        String privateBody = "private-provider-content";
        try (LocalEmbeddingServer server = new LocalEmbeddingServer((exchange, sequence) -> {
            String vector = sequence == 0 ? "[0.1,0.2]" : "[0.3]";
            respond(exchange, 200,
                    "{\"model\":\"embeddinggemma\",\"embeddings\":[" + vector
                            + "],\"detail\":\"" + privateBody + "\"}", "application/json");
        })) {
            List<DocumentChunk> chunks = List.of(
                    new DocumentChunk("notes.txt", 0, 0, 3, "one"),
                    new DocumentChunk("notes.txt", 1, 3, 6, "two"));

            OllamaEmbeddingClient.Result result = client(server.endpoint()).embed(
                    new OllamaEmbeddingConfiguration("embeddinggemma"), chunks);

            assertEquals(OllamaEmbeddingClient.Status.INVALID_RESPONSE, result.status());
            assertTrue(result.embeddedChunks().isEmpty());
            assertFalse(result.toString().contains(privateBody));
        }
    }

    private static OllamaEmbeddingClient client(URI endpoint) {
        return new OllamaEmbeddingClient(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(1))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                endpoint, Duration.ofSeconds(5));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
            String body, String contentType) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange, int sequence) throws IOException;
    }

    private static final class LocalEmbeddingServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;

        private LocalEmbeddingServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newSingleThreadExecutor();
            int[] sequence = {0};
            server.createContext("/api/embed", exchange -> handler.handle(exchange, sequence[0]++));
            server.setExecutor(executor);
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/embed");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
