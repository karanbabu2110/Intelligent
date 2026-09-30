package io.kaos.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs a repeatable browser workflow against a temporary loopback fixture. */
final class BrowserDemoCommand {
    private final CommandContext context;

    BrowserDemoCommand(CommandContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    int execute() {
        HttpServer fixture;
        try {
            fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            context.errorOutput().println("Unable to start the local browser demo fixture.");
            return KaosApplication.APPLICATION_ERROR;
        }

        AtomicInteger nonGetRequests = new AtomicInteger();
        fixture.createContext("/", exchange -> respond(exchange, nonGetRequests));
        fixture.start();
        try {
            String baseUrl = "http://127.0.0.1:" + fixture.getAddress().getPort();
            String commands = String.join("\n",
                    "record on",
                    "inspect " + baseUrl + "/details?demo-token=hidden#demo-fragment",
                    "back",
                    "forward",
                    "back",
                    "fill #query private-demo-value",
                    "deny",
                    "fill #query private-demo-value",
                    "approve",
                    "record off",
                    "record show",
                    "close") + "\n";
            CommandContext scriptedContext = new CommandContext(
                    context.configuration(),
                    new ByteArrayInputStream(commands.getBytes(StandardCharsets.UTF_8)),
                    context.output(),
                    context.errorOutput());

            context.output().println(
                    "Running a scripted browser workflow against a temporary 127.0.0.1 fixture.");
            int sessionResult = BrowserSessionRuntime.run(
                    scriptedContext,
                    URI.create(baseUrl + "/"));
            if (sessionResult != KaosApplication.SUCCESS) return sessionResult;
            if (nonGetRequests.get() != 0) {
                context.errorOutput().println(
                        "Browser demo failed: a non-GET request reached the local fixture.");
                return KaosApplication.APPLICATION_ERROR;
            }
            context.output().println(
                    "Browser demo completed. No form submission reached the fixture; no data was persisted.");
            return KaosApplication.SUCCESS;
        } catch (LinkageError exception) {
            context.errorOutput().println(
                    "Browser demo runtime is unavailable. Run with the installed KAOS dependencies.");
            return KaosApplication.APPLICATION_ERROR;
        } finally {
            fixture.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, AtomicInteger nonGetRequests) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            nonGetRequests.incrementAndGet();
            send(exchange, 405, "Method not allowed.");
            return;
        }
        switch (exchange.getRequestURI().getPath()) {
            case "/" -> send(exchange, 200,
                    "<!doctype html><title>KAOS browser demo</title>"
                            + "<h1>Temporary local form</h1><form method=\"post\" "
                            + "action=\"/submitted\"><label>Query "
                            + "<input id=\"query\" type=\"search\"></label>"
                            + "<button type=\"submit\">Submit</button></form>");
            case "/details" -> send(exchange, 200,
                    "<!doctype html><title>Demo details</title>"
                            + "<h1>Second local page</h1><p>Navigation succeeded.</p>");
            default -> send(exchange, 404, "Local demo page not found.");
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
