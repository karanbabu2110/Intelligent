package io.kaos.app;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.PrintStream;
import java.util.Objects;

/**
 * The single process entry point for the evolving KAOS application.
 */
public final class KaosApplication {
    static final int SUCCESS = 0;
    static final int USAGE_ERROR = 2;

    private KaosApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, ApplicationConfiguration.load(), System.out, System.err);
        if (exitCode != SUCCESS) {
            System.exit(exitCode);
        }
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(errorOutput, "errorOutput");

        if (arguments.length == 0 || isCommand(arguments, "status")) {
            output.println(startupMessage(configuration));
            return SUCCESS;
        }

        if (isCommand(arguments, "help") || isCommand(arguments, "--help")) {
            output.print(helpText());
            return SUCCESS;
        }

        errorOutput.println(invalidArgumentsMessage(arguments));
        return USAGE_ERROR;
    }

    static String startupMessage(ApplicationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return configuration.applicationName() + " application baseline is running.";
    }

    static String helpText() {
        return """
                Usage: kaos [status|help]

                Commands:
                  status  Show local application status (default).
                  help    Show this help. The --help alias is also supported.
                """;
    }

    private static boolean isCommand(String[] arguments, String command) {
        return arguments.length == 1 && command.equals(arguments[0]);
    }

    private static String invalidArgumentsMessage(String[] arguments) {
        if (arguments.length > 1) {
            return "Expected at most one command. Run 'kaos help' for usage.";
        }
        return "Unknown command. Run 'kaos help' for usage.";
    }
}
