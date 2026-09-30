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
import java.util.ArrayList;
import java.util.List;
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

    @Test
    void recordsOnlyOptedInSuccessfulNavigationAndOmitsQueryAndFillValues() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (LocalPage page = new LocalPage(posts)) {
            String inspect = "inspect " + page.uri() + "?token=private-query#private-fragment";
            SessionResult result = run(page, "record show\nrecord on\n" + inspect
                    + "\nfill #query private-field-value\napprove\nrecord off\nreload\n"
                    + "record show\nrecord clear\nrecord show\nclose\n");

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertTrue(result.output().contains("inspect " + page.uri()));
            assertFalse(result.output().contains("private-query"));
            assertFalse(result.output().contains("private-fragment"));
            assertFalse(result.output().contains("private-field-value"));
            assertEquals(2, occurrences(result.output(), "No workflow steps recorded in this session."));
            assertEquals(1, occurrences(result.output(), "1. inspect " + page.uri()));
            assertEquals(0, posts.get());
        }
    }

    @Test
    void keepsOnlyTheLastTwentyRecordedSteps() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (LocalPage page = new LocalPage(posts)) {
            List<String> commands = new ArrayList<>();
            commands.add("record on");
            for (int index = 0; index < 21; index++) commands.add("reload");
            commands.add("record show");
            commands.add("close");
            SessionResult result = run(page, String.join("\n", commands) + "\n");
            String listing = result.output().substring(
                    result.output().indexOf("Recorded workflow (last "));

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertTrue(listing.startsWith("Recorded workflow (last 20 successful navigation steps"));
            assertEquals(20, occurrences(listing, ". reload " + page.uri()));
            assertFalse(listing.contains("21. reload"));
        }
    }

    @Test
    void capsEachRecordedUrlAt512CodePoints() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        try (LocalPage page = new LocalPage(posts)) {
            String longPath = "p".repeat(600);
            SessionResult result = run(page, "record on\ninspect " + page.uri().resolve(longPath)
                    + "\nrecord show\nclose\n");
            String step = result.output().lines()
                    .filter(line -> line.startsWith("1. inspect "))
                    .findFirst().orElseThrow().substring("1. inspect ".length());

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertTrue(step.codePointCount(0, step.length()) <= 512);
        }
    }

    private static int occurrences(String value, String search) {
        return value.split(java.util.regex.Pattern.quote(search), -1).length - 1;
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
