package io.kaos.diagnostics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Opt-in, invocation-scoped diagnostic data flow. Never changes execution authority. */
public final class DebugTrace implements AutoCloseable {

    private static final ThreadLocal<DebugTrace> CURRENT = new ThreadLocal<>();
    private static final int MAX_EVENT_CHARACTERS = 32_768;
    private static final int MAX_RUN_CHARACTERS = 8 * 1_048_576;
    private final DebugTrace previous;
    private final PrintStream output;
    private final String runId = UUID.randomUUID().toString();
    private final long start = System.nanoTime();
    private long sequence;
    private int written;
    private boolean exhausted;
    private boolean ownsOutput;

    private DebugTrace(boolean enabled, PrintStream output) {
        this.previous = CURRENT.get();
        this.output = enabled ? output : null;
        CURRENT.set(this);
    }

    public static DebugTrace open(boolean enabled, PrintStream output) {
        return new DebugTrace(enabled, java.util.Objects.requireNonNull(output));
    }

    public static DebugTrace configured(PrintStream output) {
        String configured = System.getProperty("kaos.debug");
        if (configured == null) configured = System.getenv("KAOS_DEBUG");
        if (!"true".equalsIgnoreCase(configured)) return open(false, output);
        String file = System.getProperty("kaos.debug.file");
        if (file == null) file = System.getenv("KAOS_DEBUG_FILE");
        if (file == null || file.isBlank()) return open(true, output);
        try {
            var stream = new PrintStream(java.nio.file.Files.newOutputStream(java.nio.file.Path.of(file),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND),
                    true, StandardCharsets.UTF_8);
            DebugTrace trace = open(true, stream);
            trace.ownsOutput = true;
            return trace;
        } catch (java.io.IOException | RuntimeException exception) {
            DebugTrace trace = open(true, output);
            event("debug.file_unavailable", () -> java.util.Map.of(
                    "fallback", "stderr", "exceptionType", exception.getClass().getSimpleName()));
            return trace;
        }
    }

    /** Suppliers are not evaluated when tracing is disabled or exhausted. */
    public static void event(String stage, Supplier<?> data) {
        DebugTrace trace = CURRENT.get();
        if (trace == null || trace.output == null || trace.exhausted) return;
        try {
            trace.new Encoder().write(stage, data.get());
        } catch (RuntimeException exception) {
            // Diagnostics must not break a command or disclose an exception's contents.
            try {
                trace.new Encoder().write("debug.encoding_failed", stage);
            } catch (RuntimeException ignored) {
                trace.exhausted = true;
            }
        }
    }

    /** Parse only bounded protocol data. Malformed payloads are not dumped unfiltered. */
    public static void json(String stage, byte[] bytes) {
        event(stage, () -> Encoder.parse(bytes));
    }

    public static void json(String stage, String text) {
        DebugTrace trace = CURRENT.get();
        if (trace == null || trace.output == null || trace.exhausted) return;
        json(stage, text.getBytes(StandardCharsets.UTF_8));
    }

    // Loaded only by an enabled trace; ordinary status/help can run without JSON libraries.
    private final class Encoder {
        private static final ObjectMapper JSON = new ObjectMapper();

        private static Object parse(byte[] bytes) {
            try {
                return JSON.readTree(bytes);
            } catch (java.io.IOException exception) {
                return java.util.Map.of("invalidJson", true, "bytes", bytes.length);
            }
        }

        private void write(String stage, Object data) {
            ObjectNode entry = JSON.createObjectNode();
            entry.put("debug", true).put("runId", runId).put("sequence", ++sequence)
                    .put("timestamp", Instant.now().toString())
                    .put("elapsedMs", (System.nanoTime() - start) / 1_000_000).put("stage", stage);
            JsonNode safe = sanitize(JSON.valueToTree(data));
            String serialized = safe.toString();
            if (serialized.length() > MAX_EVENT_CHARACTERS) {
                entry.put("truncated", true).put("originalCharacters", serialized.length());
                entry.put("dataPreview", serialized.substring(0, MAX_EVENT_CHARACTERS));
            } else {
                entry.set("data", safe);
            }
            String line = entry.toString();
            if (written + line.length() > MAX_RUN_CHARACTERS) {
                exhausted = true;
                entry.removeAll();
                entry.put("debug", true).put("runId", runId).put("sequence", sequence)
                        .put("stage", "debug.limit_reached").put("truncated", true);
                line = entry.toString();
            }
            output.println(line);
            output.flush();
            written += line.length();
        }

        private static JsonNode sanitize(JsonNode node) {
            if (node.isObject()) {
                ObjectNode copy = JSON.createObjectNode();
                node.properties().forEach(field -> {
                    String key = field.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                    if (key.equals("thinking") || key.equals("reasoning") || key.equals("reasoningcontent")
                            || key.equals("authorization") || key.equals("cookie") || key.equals("setcookie")
                            || key.contains("password") || key.contains("secret") || key.equals("apikey")
                            || key.equals("token") || key.equals("accesstoken") || key.equals("refreshtoken")
                            || key.equals("grant")) {
                        copy.put(field.getKey(), "[EXCLUDED]");
                    } else {
                        copy.set(field.getKey(), sanitize(field.getValue()));
                    }
                });
                return copy;
            }
            if (node.isArray()) {
                var copy = JSON.createArrayNode();
                node.forEach(item -> copy.add(sanitize(item)));
                return copy;
            }
            return node;
        }

    }

    @Override public void close() {
        if (ownsOutput) output.close();
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
