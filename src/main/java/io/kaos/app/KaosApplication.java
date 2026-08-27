package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.app.config.ApplicationConfiguration;
import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The single process entry point for the evolving KAOS application.
 */
public final class KaosApplication {
    static final int SUCCESS = 0;
    static final int APPLICATION_ERROR = 1;
    static final int USAGE_ERROR = 2;

    static final String INVALID_CONFIGURATION_CODE = "KAOS-CONFIG-001";
    static final String UNREADABLE_CONFIGURATION_CODE = "KAOS-CONFIG-002";
    static final String UNEXPECTED_APPLICATION_CODE = "KAOS-APP-001";
    static final String OLLAMA_CONNECTIVITY_CODE = "KAOS-AI-001";
    static final String INVALID_OLLAMA_MODEL_CODE = "KAOS-AI-CONFIG-001";
    static final String UNREADABLE_OLLAMA_MODEL_CODE = "KAOS-AI-CONFIG-002";

    private KaosApplication() {
    }

    public static void main(String[] args) {
        int exitCode = launch(
                args, ApplicationConfiguration::load, System.out, System.err);
        if (exitCode != SUCCESS) {
            System.exit(exitCode);
        }
    }

    static int launch(
            String[] arguments,
            Supplier<ApplicationConfiguration> configurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configurationLoader, "configurationLoader");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(errorOutput, "errorOutput");

        ApplicationConfiguration configuration;
        try {
            configuration = configurationLoader.get();
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_CONFIGURATION_CODE,
                    "Invalid application configuration. Check kaos.app.name or KAOS_APP_NAME and restart.");
            return APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(
                    errorOutput,
                    UNREADABLE_CONFIGURATION_CODE,
                    "Local application configuration could not be read. Check process permissions and restart.");
            return APPLICATION_ERROR;
        } catch (RuntimeException exception) {
            return logUnexpectedApplicationFailure(errorOutput);
        }

        try {
            return run(arguments, configuration, output, errorOutput);
        } catch (RuntimeException exception) {
            return logUnexpectedApplicationFailure(errorOutput);
        }
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            PrintStream output,
            PrintStream errorOutput) {
        return run(
                arguments,
                configuration,
                () -> new OllamaConnectivity().check(),
                OllamaModelConfiguration::load,
                output,
                errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            PrintStream output,
            PrintStream errorOutput) {
        return run(
                arguments,
                configuration,
                ollamaConnectivityCheck,
                OllamaModelConfiguration::load,
                output,
                errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            Supplier<OllamaModelConfiguration> ollamaModelConfigurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(ollamaConnectivityCheck, "ollamaConnectivityCheck");
        Objects.requireNonNull(ollamaModelConfigurationLoader, "ollamaModelConfigurationLoader");
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

        if (isCommand(arguments, "ollama-status")) {
            return reportOllamaStatus(ollamaConnectivityCheck.get(), output, errorOutput);
        }

        if (isCommand(arguments, "ollama-model")) {
            return reportOllamaModel(ollamaModelConfigurationLoader, output, errorOutput);
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
                Usage: kaos [status|help|ollama-status|ollama-model]

                Commands:
                  status         Show local application status (default).
                  help           Show this help. The --help alias is also supported.
                  ollama-status  Check connectivity to the local Ollama server.
                  ollama-model   Show the explicitly configured local Ollama model.
                """;
    }

    private static int reportOllamaModel(
            Supplier<OllamaModelConfiguration> configurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        try {
            OllamaModelConfiguration configuration = configurationLoader.get();
            output.println("Configured local Ollama model: " + configuration.modelName() + ".");
            return SUCCESS;
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_OLLAMA_MODEL_CODE,
                    "Invalid Ollama model configuration. Check kaos.ollama.model or "
                            + "KAOS_OLLAMA_MODEL and retry.");
            return APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(
                    errorOutput,
                    UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return APPLICATION_ERROR;
        }
    }

    private static int reportOllamaStatus(
            OllamaConnectivity.Result result,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(result, "result");
        if (result.reachable()) {
            output.println("Local Ollama is reachable (version " + result.version() + ").");
            return SUCCESS;
        }

        String recovery = switch (result.status()) {
            case UNAVAILABLE ->
                    "Local Ollama is unavailable. Start Ollama on 127.0.0.1:11434 and retry.";
            case INVALID_RESPONSE ->
                    "Local Ollama returned an invalid version response. Verify Ollama and retry.";
            case INTERRUPTED ->
                    "The Ollama connectivity check was interrupted. Retry the command.";
            case REACHABLE -> throw new IllegalStateException("Reachable result has no version.");
        };
        logError(errorOutput, OLLAMA_CONNECTIVITY_CODE, recovery);
        return APPLICATION_ERROR;
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

    private static void logError(PrintStream errorOutput, String code, String message) {
        errorOutput.println("ERROR [" + code + "] " + message);
    }

    private static int logUnexpectedApplicationFailure(PrintStream errorOutput) {
        logError(
                errorOutput,
                UNEXPECTED_APPLICATION_CODE,
                "KAOS could not complete the requested command. Restart and retry.");
        return APPLICATION_ERROR;
    }
}
