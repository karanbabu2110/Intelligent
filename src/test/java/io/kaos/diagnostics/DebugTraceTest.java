package io.kaos.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DebugTraceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;

    @Test
    void fileSinkAppendsUtf8RunsAndDisabledModeDoesNotCreateFiles() throws Exception {
        String previousDebug = System.getProperty("kaos.debug");
        String previousFile = System.getProperty("kaos.debug.file");
        var file = root.resolve("trace.jsonl");
        var console = new ByteArrayOutputStream();
        try {
            System.setProperty("kaos.debug.file", file.toString());
            System.setProperty("kaos.debug", "false");
            try (var trace = DebugTrace.configured(new PrintStream(console))) {
                DebugTrace.event("disabled", () -> Map.of("text", "hidden"));
            }
            assertFalse(java.nio.file.Files.exists(file));
            System.setProperty("kaos.debug", "true");
            for (int i = 0; i < 2; i++) {
                try (var trace = DebugTrace.configured(new PrintStream(console))) {
                    DebugTrace.event("input", () -> Map.of("text", "தமிழ்\n" + "x".repeat(500)));
                }
            }
            var lines = java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(2, lines.size());
            assertNotEquals(JSON.readTree(lines.get(0)).path("runId"),
                    JSON.readTree(lines.get(1)).path("runId"));
            assertTrue(JSON.readTree(lines.get(0)).path("data").path("text").asText().startsWith("தமிழ்"));
            assertEquals(0, console.size());
            System.setProperty("kaos.debug.file", root.toString());
            try (var trace = DebugTrace.configured(new PrintStream(console))) {
                DebugTrace.event("continued", () -> Map.of("ok", true));
            }
            assertTrue(console.toString(StandardCharsets.UTF_8).contains("debug.file_unavailable"));
        } finally {
            if (previousDebug == null) System.clearProperty("kaos.debug");
            else System.setProperty("kaos.debug", previousDebug);
            if (previousFile == null) System.clearProperty("kaos.debug.file");
            else System.setProperty("kaos.debug.file", previousFile);
        }
    }

    @Test
    void systemPropertyControlsInvocationAndOverridesEnvironment() {
        String previous = System.getProperty("kaos.debug");
        var bytes = new ByteArrayOutputStream();
        try {
            System.setProperty("kaos.debug", "true");
            try (var trace = DebugTrace.configured(new PrintStream(bytes))) {
                DebugTrace.event("enabled", () -> Map.of("ok", true));
            }
            System.setProperty("kaos.debug", "false");
            try (var trace = DebugTrace.configured(new PrintStream(bytes))) {
                DebugTrace.event("disabled", () -> { throw new AssertionError("must not evaluate"); });
            }
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("enabled"));
            assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("disabled"));
        } finally {
            if (previous == null) System.clearProperty("kaos.debug");
            else System.setProperty("kaos.debug", previous);
        }
    }

    @Test
    void disabledDoesNotEvaluateDataAndScopeDoesNotLeak() {
        var bytes = new ByteArrayOutputStream();
        try (var trace = DebugTrace.open(false, new PrintStream(bytes))) {
            DebugTrace.event("disabled", () -> { throw new AssertionError("must not evaluate"); });
        }
        DebugTrace.event("outside", () -> { throw new AssertionError("scope leaked"); });
        assertEquals("", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void ordersEventsEscapesUntrustedTextAndExcludesCredentialAndReasoningFields() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var trace = DebugTrace.open(true, new PrintStream(bytes))) {
            DebugTrace.event("input", () -> Map.of("content", "line\n\u001b[31m",
                    "nested", Map.of("thinking", "private-reasoning", "api_key", "credential",
                            "grant", "approval-secret"), "num_predict", 2048));
            DebugTrace.event("output", () -> Map.of("answer", "answer"));
        }
        String text = bytes.toString(StandardCharsets.UTF_8);
        var lines = text.lines().toList();
        assertEquals(2, lines.size());
        var first = JSON.readTree(lines.get(0));
        var second = JSON.readTree(lines.get(1));
        assertEquals(first.path("runId"), second.path("runId"));
        assertEquals(1, first.path("sequence").asInt());
        assertEquals(2, second.path("sequence").asInt());
        assertEquals(2048, first.path("data").path("num_predict").asInt());
        assertFalse(text.contains("private-reasoning"));
        assertFalse(text.contains("credential"));
        assertFalse(text.contains("approval-secret"));
        assertFalse(text.contains("\u001b"));
    }

    @Test
    void truncatesOversizedEventsAndStopsAfterRunBudget() {
        var bytes = new ByteArrayOutputStream();
        try (var trace = DebugTrace.open(true, new PrintStream(bytes))) {
            for (int i = 0; i < 300; i++) {
                DebugTrace.event("large", () -> Map.of("content", "x".repeat(40_000)));
            }
        }
        String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("\"truncated\":true"));
        assertTrue(text.contains("debug.limit_reached"));
        assertTrue(text.length() < 8 * 1_048_576 + 1000);
    }

    @Test
    void malformedJsonAndBrokenSuppliersDoNotExposeRawDataOrBreakExecution() {
        var bytes = new ByteArrayOutputStream();
        try (var trace = DebugTrace.open(true, new PrintStream(bytes))) {
            DebugTrace.json("bad", "private malformed payload");
            DebugTrace.event("broken", () -> { throw new IllegalArgumentException("private detail"); });
            DebugTrace.event("after", () -> Map.of("ok", true));
        }
        String text = bytes.toString(StandardCharsets.UTF_8);
        assertFalse(text.contains("private"));
        assertTrue(text.contains("invalidJson"));
        assertTrue(text.contains("debug.encoding_failed"));
        assertTrue(text.contains("after"));
    }
}
