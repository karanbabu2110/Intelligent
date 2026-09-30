package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrowserSessionCommandTest {
    @Test
    void rejectsNonLoopbackUrlBeforeStartingBrowser() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));

        assertEquals(KaosApplication.USAGE_ERROR,
                new BrowserSessionCommand(context).execute("https://example.com"));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("no session was started"));
    }
}
