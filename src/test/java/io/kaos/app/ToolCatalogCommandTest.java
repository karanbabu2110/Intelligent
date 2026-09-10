package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.websearch.SearxngClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCatalogCommandTest {
    @TempDir Path root;

    @Test void listsFixedToolsInStableOrderWithoutExposingConfiguration() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String privateHost = "private.example";
        String privateEndpoint = "http://127.0.0.1:8080";
        var command = new ToolCatalogCommand(context(output),
                () -> new ReadLocalFilePermissionValidator(root),
                () -> new HttpGetPermissionValidator(Set.of(privateHost)),
                () -> new SearxngClient(privateEndpoint));

        assertEquals(KaosApplication.SUCCESS, command.execute());

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.indexOf("read_local_file") < text.indexOf("http_get"));
        assertTrue(text.indexOf("http_get") < text.indexOf("web_search"));
        assertEquals(3, text.split("configuration: configured", -1).length - 1);
        assertFalse(text.contains(root.toString()));
        assertFalse(text.contains(privateHost));
        assertFalse(text.contains(privateEndpoint));
        assertTrue(text.contains("no file, DNS, service, or web request was made"));
    }

    @Test void reportsUnavailableConfigurationsWithoutFailingOrLeakingErrors() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicInteger calls = new AtomicInteger();
        var command = new ToolCatalogCommand(context(output),
                () -> {
                    calls.incrementAndGet();
                    return new ReadLocalFilePermissionValidator(Path.of("relative"));
                },
                () -> {
                    calls.incrementAndGet();
                    return new HttpGetPermissionValidator(Set.of());
                },
                () -> {
                    calls.incrementAndGet();
                    return new SearxngClient(null);
                });

        assertEquals(KaosApplication.SUCCESS, command.execute());

        String text = output.toString(StandardCharsets.UTF_8);
        assertEquals(3, calls.get());
        assertEquals(3, text.split("configuration: unavailable", -1).length - 1);
        assertFalse(text.contains("relative"));
    }

    @Test void doesNotHideUnexpectedProgrammingFailures() {
        var command = new ToolCatalogCommand(context(new ByteArrayOutputStream()),
                () -> { throw new IllegalStateException("unexpected"); },
                () -> new HttpGetPermissionValidator(Set.of("example.com")),
                () -> new SearxngClient("http://127.0.0.1:8080"));

        assertThrows(IllegalStateException.class, command::execute);
    }

    @Test void consumesAnInjectedRegistryWithoutExecutingOrAdvertisingItsTools() {
        var tool = new io.kaos.tool.FixtureTool();
        var output = new ByteArrayOutputStream();
        var command = new ToolCatalogCommand(context(output),
                new io.kaos.tool.ToolRegistry(java.util.List.of(tool)));
        assertEquals(0, command.execute());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains(
                "fixture_tool | Fixture purpose | approval: required | configuration: configured"));
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("Different model wording"));
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("read_local_file"));
        assertEquals(0, tool.executions());
        assertEquals(0, tool.preparations());
    }

    private static CommandContext context(ByteArrayOutputStream output) {
        return new CommandContext(new ApplicationConfiguration("KAOS"),
                InputStream.nullInputStream(),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }
}
