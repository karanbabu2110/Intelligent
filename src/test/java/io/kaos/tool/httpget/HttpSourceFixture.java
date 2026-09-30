package io.kaos.tool.httpget;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit test-only loopback mapping; production URL and DNS policies have no fixture switch. */
public final class HttpSourceFixture implements AutoCloseable {
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService workers = Executors.newCachedThreadPool();
    public final AtomicInteger calls = new AtomicInteger();
    public final AtomicInteger resolutions = new AtomicInteger();
    public final List<String> paths = new CopyOnWriteArrayList<>();
    public final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
    public final Map<String, String> bodies = new java.util.concurrent.ConcurrentHashMap<>();
    public final Map<String, String> responseHeaders = new java.util.concurrent.ConcurrentHashMap<>();
    public final Map<String, Integer> statuses = new java.util.concurrent.ConcurrentHashMap<>();
    public final Map<String, HttpGetException.Reason> failures = new java.util.concurrent.ConcurrentHashMap<>();
    private final byte[] content;
    private final String media;
    private final int status;
    private final boolean stall;

    public HttpSourceFixture(String content, String media, int status, boolean stall) throws IOException {
        this.content = content.getBytes(StandardCharsets.UTF_8);
        this.media = media;
        this.status = status;
        this.stall = stall;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            paths.add(exchange.getRequestURI().toASCIIString());
            headers.add(Map.copyOf(exchange.getRequestHeaders()));
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", media);
                exchange.getResponseHeaders().set("Location", "http://127.0.0.1/never");
                exchange.getResponseHeaders().set("Set-Cookie", "secret=never-send");
                responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
                int code = statuses.getOrDefault(exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getPath().equals("/fail") ? 503 : status);
                exchange.sendResponseHeaders(code, 0);
                if (stall) {
                    exchange.getResponseBody().write('x');
                    exchange.getResponseBody().flush();
                    try { Thread.sleep(5000); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                } else {
                    exchange.getResponseBody().write(bodies.getOrDefault(exchange.getRequestURI().getPath(),
                            new String(this.content, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) { /* Client intentionally aborts rejected responses. */ }
        });
        server.start();
    }

    public HttpGetPermissionValidator validator() {
        return new HttpGetPermissionValidator(Set.of("one.example", "two.example", "three.example"));
    }

    public HttpGet tool() {
        return tool(this::validator);
    }

    public HttpGet tool(java.util.function.Supplier<HttpGetPermissionValidator> configuration) {
        return new HttpGet(configuration, (validator, grant) -> {
            var target = grant.claim();
            var failure = failures.get(target.uri().getPath());
            if (failure != null) throw new HttpGetException(failure);
            return retrieve(target, Duration.ofSeconds(2));
        });
    }

    HttpGetResult retrieve(HttpGetTarget approved, Duration timeout) {
        return PinnedHttpTransport.execute(target -> {
            resolutions.incrementAndGet();
            return InetAddress.getLoopbackAddress();
        }, mapped(approved), timeout);
    }

    HttpGetTarget mapped(HttpGetTarget approved) {
        var uri = approved.uri();
        URI mapped = URI.create("http://" + uri.getHost() + ":" + server.getAddress().getPort()
                + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        return new HttpGetTarget(approved.request(), mapped);
    }

    @Override public void close() {
        server.stop(0);
        workers.shutdownNow();
    }
}
