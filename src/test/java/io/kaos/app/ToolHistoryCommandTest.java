package io.kaos.app;

import static org.junit.jupiter.api.Assertions.*;

import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.tool.ToolExecutionOutcome;
import io.kaos.tool.history.ToolExecutionRecord;
import io.kaos.tool.permission.ToolPermissionDecision;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolHistoryCommandTest {
    @Test void displaysBoundedNewestFirstContentFreeHistory() {
        var history = new RecordingToolHistory();
        history.record(record("read_local_file", ToolPermissionDecision.DENIED,
                ToolExecutionOutcome.DENIED, "2026-09-10T00:00:00Z"));
        history.record(record("web_search", ToolPermissionDecision.APPROVED,
                ToolExecutionOutcome.SUCCEEDED, "2026-09-10T00:01:00Z"));
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();

        assertEquals(KaosApplication.SUCCESS,
                new ToolHistoryCommand(context(output, errors), () -> history).execute());

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.indexOf("web_search") < text.indexOf("read_local_file"));
        assertTrue(text.contains("content is never stored"));
        assertFalse(text.contains("path"));
        assertFalse(text.contains("query"));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test void reportsEmptyAndUnavailableHistory() {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        assertEquals(KaosApplication.SUCCESS,
                new ToolHistoryCommand(context(output, errors), RecordingToolHistory::new).execute());
        assertEquals("No tool executions recorded." + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8));

        var unavailableOutput = new ByteArrayOutputStream();
        var unavailableErrors = new ByteArrayOutputStream();
        assertEquals(KaosApplication.APPLICATION_ERROR,
                new ToolHistoryCommand(context(unavailableOutput, unavailableErrors),
                        () -> { throw new IllegalArgumentException("private path"); }).execute());
        assertEquals("", unavailableOutput.toString(StandardCharsets.UTF_8));
        assertTrue(unavailableErrors.toString(StandardCharsets.UTF_8)
                .contains("KAOS-TOOL-HISTORY-001"));
    }

    private static ToolExecutionRecord record(String tool, ToolPermissionDecision decision,
            ToolExecutionOutcome outcome, String time) {
        Instant instant = Instant.parse(time);
        return new ToolExecutionRecord(tool, UUID.randomUUID(), Optional.of(decision), outcome,
                instant, instant.plusSeconds(1));
    }

    private static CommandContext context(ByteArrayOutputStream output, ByteArrayOutputStream errors) {
        return new CommandContext(new ApplicationConfiguration("KAOS"), InputStream.nullInputStream(),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
    }
}
