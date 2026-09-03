package io.kaos.app;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Immutable process resources shared by application commands.
 */
record CommandContext(
        ApplicationConfiguration configuration,
        InputStream input,
        PrintStream output,
        PrintStream errorOutput) {

    CommandContext {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(errorOutput, "errorOutput");
    }
}
