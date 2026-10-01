package io.kaos.tool.browserrender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetTarget;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PinnedBrowserResourceFetcherTest {
    @Test void fetchesOnePinnedBoundedResponseWithoutCredentialsOrRedirectFollowing() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.status = 302;
            fixture.location = "/next";
            BrowserResource response = fixture.fetch("/start", 1024, Duration.ofSeconds(2));

            assertEquals(302, response.status());
            assertEquals("/next", response.location());
            assertEquals(0, response.body().length);
            assertEquals(1, fixture.calls.get());
            assertEquals(List.of("/start"), fixture.paths);
            fixture.headers.forEach(headers -> headers.keySet().forEach(name -> {
                assertFalse(name.equalsIgnoreCase("cookie"));
                assertFalse(name.equalsIgnoreCase("authorization"));
                assertFalse(name.equalsIgnoreCase("proxy-authorization"));
            }));
        }
    }

    @Test void enforcesDeclaredAndStreamedByteBoundsWithoutRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.body = "x".repeat(1025);
            assertReason(BrowserRenderException.Reason.TOO_LARGE,
                    () -> fixture.fetch("/large", 1024, Duration.ofSeconds(2)));
            assertEquals(1, fixture.calls.get());

            fixture.body = "x".repeat(1024);
            assertEquals(1024, fixture.fetch("/exact", 1024, Duration.ofSeconds(2)).body().length);
            assertEquals(2, fixture.calls.get());
        }
    }

    @Test void boundsCompleteBodyTimeAndInterruptsTheNetworkWorker() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.stall = true;
            long started = System.nanoTime();
            assertReason(BrowserRenderException.Reason.TIMEOUT,
                    () -> fixture.fetch("/slow", 1024, Duration.ofMillis(200)));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2000);
            assertEquals(1, fixture.calls.get());
        }
    }

    private static void assertReason(BrowserRenderException.Reason reason, Runnable request) {
        assertEquals(reason, assertThrows(BrowserRenderException.class, request::run).reason());
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService workers = Executors.newCachedThreadPool();
        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
        private volatile String body = "resource";
        private volatile int status = 200;
        private volatile String location = "";
        private volatile boolean stall;

        private Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::handle);
            server.start();
        }

        private BrowserResource fetch(String path, int maxBytes, Duration timeout) {
            URI mapped = URI.create("http://one.example:" + server.getAddress().getPort() + path);
            HttpGetTarget target = new HttpGetTarget(new HttpGetRequest("https://one.example" + path), mapped);
            return PinnedBrowserResourceFetcher.execute(ignored -> InetAddress.getLoopbackAddress(),
                    target, timeout, maxBytes);
        }

        private void handle(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            paths.add(exchange.getRequestURI().toASCIIString());
            headers.add(Map.copyOf(exchange.getRequestHeaders()));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                if (!location.isBlank()) exchange.getResponseHeaders().set("Location", location);
                exchange.sendResponseHeaders(status, bytes.length);
                if (stall) {
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                    try { Thread.sleep(5000); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                } else {
                    exchange.getResponseBody().write(bytes);
                }
            } catch (IOException ignored) { }
        }

        @Override public void close() {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
