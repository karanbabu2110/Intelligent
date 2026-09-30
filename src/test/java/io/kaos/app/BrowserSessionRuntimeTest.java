package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BrowserSessionRuntimeTest {
    @Test
    void fillsOnlyAfterApprovalAndNeverSubmitsOrEchoesTheValue() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (LocalPage page = new LocalPage(posts)) {
            SessionResult result = run(page, "fill #query private-value\napprove\nclose\n");

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertTrue(result.output().contains("Type approve or deny"));
            assertTrue(result.output().contains("Filled one eligible text field"));
            assertFalse(result.output().contains("private-value"));
            assertEquals(0, posts.get());
        }
    }

    @Test
    void denialAndEndOfInputCancelWithoutFilling() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (LocalPage page = new LocalPage(posts)) {
            SessionResult denied = run(page, "fill #query private-value\ndeny\nclose\n");
            SessionResult eof = run(page, "fill #query private-value\n");

            assertEquals(KaosApplication.SUCCESS, denied.exitCode());
            assertTrue(denied.output().contains("Fill cancelled; approval was not granted."));
            assertTrue(eof.output().contains("Fill cancelled; approval was not granted."));
            assertFalse(denied.output().contains("private-value"));
            assertFalse(eof.output().contains("private-value"));
            assertEquals(0, posts.get());
        }
    }

    @Test
    void restoresTheLastSuccessfulPageAfterAnInteractiveNavigationFailure() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (LocalPage page = new LocalPage(posts, true)) {
            SessionResult result = run(page, "reload\ninspect\nclose\n");

            assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
            assertTrue(result.output().contains("Recovered the last successfully loaded page."),
                    result.output());
            assertTrue(result.output().contains("Browser session is open."));
            assertTrue(result.output().contains("Browser session closed."));
            assertTrue(result.output().split("Browser inspection complete", -1).length >= 3);
            assertEquals(0, posts.get());
        }
    }

    private static SessionResult run(LocalPage page, String commands) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        int exitCode = BrowserSessionRuntime.run(context, page.uri());
        return new SessionResult(exitCode,
                output.toString(StandardCharsets.UTF_8) + error.toString(StandardCharsets.UTF_8));
    }

    private record SessionResult(int exitCode, String output) { }

    private static final class LocalPage implements AutoCloseable {
        private final HttpServer server;

        private LocalPage(AtomicInteger posts) throws Exception {
            this(posts, false);
        }

        private LocalPage(AtomicInteger posts, boolean failSecondGet) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            AtomicInteger gets = new AtomicInteger();
            server.createContext("/", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())
                        && failSecondGet && gets.incrementAndGet() == 2) {
                    byte[] failure = "temporary failure".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(503, failure.length);
                    exchange.getResponseBody().write(failure);
                    exchange.close();
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) posts.incrementAndGet();
                byte[] body = "<form method=post><input id=query type=search><button>Submit</button></form>"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        }

        private java.net.URI uri() {
            return java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
