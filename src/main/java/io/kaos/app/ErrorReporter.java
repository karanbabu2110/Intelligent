package io.kaos.app;

import java.io.PrintStream;
import java.util.Objects;

/** Writes stable, content-safe application errors. */
final class ErrorReporter {
    private ErrorReporter() {
    }

    static void report(PrintStream errorOutput, String code, String message) {
        Objects.requireNonNull(errorOutput, "errorOutput");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        io.kaos.diagnostics.DebugTrace.event("application.error", () ->
                java.util.Map.of("code", code, "message", message));
        errorOutput.println("ERROR [" + code + "] " + message);
    }
}
