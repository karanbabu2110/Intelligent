package io.kaos.app.browser;
import io.kaos.app.KaosApplication;

import io.kaos.app.CommandContext;
import io.kaos.app.config.ApplicationConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BrowserInspectRuntimeTest {
    @Test
    void rejectsCrossPortRedirectsFromTheSelectedOrigin() throws Exception {
        AtomicInteger destinationRequests = new AtomicInteger();
        try (PageServer destination = new PageServer(exchange -> {
            destinationRequests.incrementAndGet();
            respond(exchange, 200, "<title>other service</title>must not load");
        });
             PageServer source = new PageServer(exchange -> {
                 exchange.getResponseHeaders().set("Location", destination.uri().toString());
                 exchange.sendResponseHeaders(302, -1);
                 exchange.close();
             })) {
            assertTrue(source.uri().getPort() != destination.uri().getPort());
            assertFalse(BrowserInspectCommand.allowRequest(
                    destination.uri().toString(), "GET", "document", source.uri()));
            RuntimeResult result = inspect(source.uri());

            assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode(),
                    source.uri() + " -> " + destination.uri() + System.lineSeparator()
                            + result.output() + result.error());
            assertFalse(result.output().contains("Browser inspection complete"), result.output());
            assertTrue(result.error().contains("Browser inspection failed"), result.error());
            assertEquals(0, destinationRequests.get());
        }
    }

    @Test
    void allowsRedirectsThatStayOnTheSelectedOrigin() throws Exception {
        try (PageServer server = new PageServer(exchange -> {
            if ("/redirect".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().set("Location", "/final");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            } else {
                respond(exchange, 200, "<title>final page</title>same-origin destination");
            }
        })) {
            RuntimeResult result = inspect(server.uri().resolve("redirect"));

            assertEquals(KaosApplication.SUCCESS, result.exitCode(), result.error());
            assertTrue(result.output().contains("Title: final page"), result.output());
            assertTrue(result.output().contains("same-origin destination"), result.output());
            assertTrue(result.output().contains(server.uri().resolve("final").toString()), result.output());
        }
    }

    @Test
    void treatsHttpErrorResponsesAsFailedInspections() throws Exception {
        try (PageServer errorPage = new PageServer(exchange ->
                respond(exchange, 404, "<title>not found</title>local error page"))) {
            RuntimeResult result = inspect(errorPage.uri());

            assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
            assertFalse(result.output().contains("Browser inspection complete"), result.output());
            assertTrue(result.error().contains("Browser inspection failed"), result.error());
        }
    }

    @Test
    void extractsOnlyBoundedVisibleTextFromALargeDocument() throws Exception {
        String document = "<title>large</title><body>" + "x".repeat(3999)
                + "😀TAIL" + "y".repeat(12000) + "</body>";
        try (PageServer largePage = new PageServer(exchange -> respond(exchange, 200, document))) {
            RuntimeResult result = inspect(largePage.uri());

            assertEquals(KaosApplication.SUCCESS, result.exitCode());
            assertTrue(result.output().contains("[Visible text truncated at the extraction limit.]"));
            assertFalse(result.output().contains("TAIL"));
            assertTrue(result.output().length() < 4500,
                    Integer.toString(result.output().length()));
            int headingEnd = result.output().indexOf(System.lineSeparator(),
                    result.output().indexOf("Visible text:")) + System.lineSeparator().length();
            int markerStart = result.output().indexOf("\n[Visible text truncated", headingEnd);
            assertEquals(4000,
                    result.output().substring(headingEnd, markerStart)
                            .codePointCount(0, markerStart - headingEnd));
        }
    }

    private static RuntimeResult inspect(URI uri) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                java.io.InputStream.nullInputStream(),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));
        int exitCode = BrowserInspectRuntime.inspect(context, uri);
        return new RuntimeResult(exitCode, output.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RuntimeResult(int exitCode, String output, String error) { }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class PageServer implements AutoCloseable {
        private final HttpServer server;

        private PageServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler::handle);
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
