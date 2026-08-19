package io.kaos.app;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.PrintStream;
import java.util.Objects;

/**
 * The single process entry point for the evolving KAOS application.
 */
public final class KaosApplication {
    private KaosApplication() {
    }

    public static void main(String[] args) {
        run(ApplicationConfiguration.load(), System.out);
    }

    static void run(ApplicationConfiguration configuration, PrintStream output) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(output, "output");
        output.println(startupMessage(configuration));
    }

    static String startupMessage(ApplicationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return configuration.applicationName() + " application baseline is running.";
    }
}
