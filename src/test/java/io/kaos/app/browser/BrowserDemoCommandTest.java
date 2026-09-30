package io.kaos.app.browser;
import io.kaos.app.KaosApplication;

import io.kaos.app.CommandContext;
import io.kaos.app.config.ApplicationConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrowserDemoCommandTest {
    @Test
    void runsTheBrowserFlowAndConfirmsThatNothingWasSubmitted() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));

        int exitCode = new BrowserDemoCommand(context).execute();
        String result = output.toString(StandardCharsets.UTF_8)
                + error.toString(StandardCharsets.UTF_8);

        assertEquals(KaosApplication.SUCCESS, exitCode, result);
        assertTrue(result.contains("Demo details"), result);
        assertTrue(result.contains("Workflow recording started"), result);
        assertTrue(result.contains("Type approve or deny"), result);
        assertTrue(result.contains("Fill cancelled; approval was not granted."), result);
        assertTrue(result.contains("Filled one eligible text field"), result);
        assertTrue(result.contains("Recorded workflow"), result);
        assertTrue(result.contains("No form submission reached the fixture; no data was persisted."), result);
        assertFalse(result.contains("demo-token"), result);
        assertFalse(result.contains("demo-fragment"), result);
        assertFalse(result.contains("private-demo-value"), result);
    }
}
