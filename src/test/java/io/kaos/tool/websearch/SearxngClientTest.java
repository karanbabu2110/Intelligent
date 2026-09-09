package io.kaos.tool.websearch;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearxngClientTest {
    private static final String ENTRY = "{\"title\":\"Spring Boot\",\"url\":\"https://spring.io\",\"content\":\"snippet\",\"engine\":\"private\"}";
    private static WebSearchApproval.Grant grant() {
        return new WebSearchApproval(new WebSearchRequest("spring & café 😀"))
                .decide("approve").grant().orElseThrow();
    }
    @Test void sendsOnlyEncodedApprovedQueryAndNormalizesFiveOrderedResults() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> query = new java.util.concurrent.atomic.AtomicReference<>();
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] body = ("{\"results\":[" + String.join(",", java.util.Collections.nCopies(7, ENTRY))
                    + "],\"debug\":\"secret metadata\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new SearxngClient("http://127.0.0.1:" + server.getAddress().getPort());
            var approval = grant();
            var result = client.execute(approval);
            assertEquals("q=spring+%26+caf%C3%A9+%F0%9F%98%80&format=json", query.get());
            assertEquals(1, calls.get());
            assertEquals(5, result.results().size());
            assertEquals("Spring Boot", result.results().getFirst().title());
            String encoded = WebSearchToolContract.encodeResult(result).toString();
            assertFalse(encoded.contains("engine"));
            assertFalse(encoded.contains("metadata"));
            assertThrows(WebSearchException.class, () -> client.execute(approval));
            assertEquals(1, calls.get());
        } finally { server.stop(0); }
    }
    @Test void emptyResultsAreSuccess() throws Exception {
        assertEquals(0, response(200, "{\"results\":[]}").results().size());
    }
    @Test void rejectsMalformedMissingOversizedOrUnsafeFields() {
        for (String body : List.of("not json", "{}", "{\"results\":null}", "{\"results\":[{}]}",
                "{\"results\":[]} trailing", "{\"results\":[],\"results\":[]}",
                "{\"results\":[{\"title\":\"x\",\"url\":\"javascript:evil\"}]}",
                "{\"results\":[{\"title\":\"x\",\"url\":\"https://example.com\",\"content\":4}]}",
                "{\"results\":[" + ENTRY.replace("Spring Boot", "x".repeat(257)) + "]}",
                "{\"results\":[" + ENTRY.replace("snippet", "x".repeat(513)) + "]}",
                "{\"results\":[" + ENTRY.replace("https://spring.io", "https://example.com/" + "x".repeat(2048)) + "]}",
                "{\"results\":[" + ENTRY.replace("Spring Boot", "") + "]}")) {
            assertEquals(WebSearchException.Reason.INVALID_RESPONSE,
                    assertThrows(WebSearchException.class, () -> response(200, body)).reason());
        }
        assertEquals(WebSearchException.Reason.RESULT_TOO_LARGE,
                assertThrows(WebSearchException.class, () -> response(200, "x".repeat(65_537))).reason());
        for (int code : new int[] {302, 403, 429, 500}) {
            var exception = assertThrows(WebSearchException.class, () -> response(code, "private failure"));
            assertFalse(exception.toString().contains("private failure"));
            assertNull(exception.getCause());
        }
    }
    private static WebSearchResult response(int code, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Location", "/must-not-follow");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            return new SearxngClient("http://127.0.0.1:" + server.getAddress().getPort()).execute(grant());
        } finally { server.stop(0); }
    }
    @Test void deadlineCoversStalledBodyAndDoesNotRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/search", exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var client = new SearxngClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(300));
            assertEquals(WebSearchException.Reason.SEARCH_TIMEOUT,
                    assertThrows(WebSearchException.class, () -> client.execute(grant())).reason());
            assertEquals(1, calls.get());
        } finally { release.countDown(); server.stop(0); }
    }
    @Test void connectionFailureIsSafe() throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0)) { port = reserved.getLocalPort(); }
        var exception = assertThrows(WebSearchException.class,
                () -> new SearxngClient("http://127.0.0.1:" + port).execute(grant()));
        assertEquals(WebSearchException.Reason.SEARCH_SERVICE_UNAVAILABLE, exception.reason());
        assertFalse(exception.toString().contains(Integer.toString(port)));
    }
}
