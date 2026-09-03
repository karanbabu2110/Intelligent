package io.kaos.app;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    static final String OLLAMA_PROMPT_CODE = "KAOS-AI-002";
    static final String OLLAMA_RESPONSE_LIMIT_CODE = "KAOS-AI-003";
    static final String OLLAMA_STREAM_CODE = "KAOS-AI-004";
    static final String OLLAMA_TIMEOUT_CODE = "KAOS-AI-005";
    static final String OLLAMA_CANCELLATION_CODE = "KAOS-AI-006";
    static final String CONVERSATION_INPUT_CODE = "KAOS-CONVERSATION-001";
    static final String CONVERSATION_STORAGE_CODE = "KAOS-CONVERSATION-002";
    static final String INVALID_KNOWLEDGE_DOCUMENT_CODE = "KAOS-KNOWLEDGE-001";
    static final String UNAVAILABLE_KNOWLEDGE_DOCUMENT_CODE = "KAOS-KNOWLEDGE-002";
    static final String KNOWLEDGE_DOCUMENT_LIMIT_CODE = "KAOS-KNOWLEDGE-003";
    static final String KNOWLEDGE_TEXT_EXTRACTION_CODE = "KAOS-KNOWLEDGE-004";

    private KaosApplication() {
    }

    public static void main(String[] args) {
        Thread commandThread = Thread.currentThread();
        CountDownLatch commandFinished = new CountDownLatch(1);
        Thread cancellationHook = new Thread(() -> {
            commandThread.interrupt();
            awaitCommandCleanup(commandFinished);
        }, "kaos-command-cancellation");
        Runtime runtime = Runtime.getRuntime();
        boolean hookRegistered = false;
        int exitCode;
        try {
            try {
                runtime.addShutdownHook(cancellationHook);
                hookRegistered = true;
            } catch (IllegalStateException | SecurityException exception) {
                // The normal command boundary still handles direct thread interruption.
            }
            exitCode = launch(
                    args, ApplicationConfiguration::load, System.in, System.out, System.err);
        } finally {
            commandFinished.countDown();
            if (hookRegistered) {
                try {
                    runtime.removeShutdownHook(cancellationHook);
                } catch (IllegalStateException | SecurityException exception) {
                    // Shutdown already owns the hook, or the runtime denied hook removal.
                }
            }
        }
        if (exitCode != SUCCESS && !commandThread.isInterrupted()) {
            System.exit(exitCode);
        }
    }

    private static void awaitCommandCleanup(CountDownLatch commandFinished) {
        try {
            commandFinished.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    static int launch(
            String[] arguments,
            Supplier<ApplicationConfiguration> configurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        return launch(arguments, configurationLoader, InputStream.nullInputStream(),
                output, errorOutput);
    }

    static int launch(
            String[] arguments,
            Supplier<ApplicationConfiguration> configurationLoader,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configurationLoader, "configurationLoader");
        Objects.requireNonNull(input, "input");
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
            return run(arguments, configuration, input, output, errorOutput);
        } catch (RuntimeException exception) {
            return logUnexpectedApplicationFailure(errorOutput);
        }
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            PrintStream output,
            PrintStream errorOutput) {
        return run(arguments, configuration, InputStream.nullInputStream(), output, errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        return ApplicationRuntime.local()
                .execute(arguments, configuration, input, output, errorOutput);
    }

    static String startupMessage(ApplicationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return configuration.applicationName() + " application baseline is running.";
    }

    static String helpText() {
        return """
                Usage: kaos [status|help|knowledge-ingest <path>|ollama-status|ollama-model|ollama-prompt <prompt>|conversation]

                Commands:
                  status         Show local application status (default).
                  help           Show this help. The --help alias is also supported.
                  knowledge-ingest  Admit one local UTF-8 .txt document up to 1 MiB.
                  ollama-status  Check connectivity to the local Ollama server.
                  ollama-model   Show the explicitly configured local Ollama model.
                  ollama-prompt  Submit one quoted prompt and stream the answer.
                  conversation   Start selectable persistent local conversations.
                """;
    }

    private static void logError(PrintStream errorOutput, String code, String message) {
        ErrorReporter.report(errorOutput, code, message);
    }

    private static int logUnexpectedApplicationFailure(PrintStream errorOutput) {
        logError(
                errorOutput,
                UNEXPECTED_APPLICATION_CODE,
                "KAOS could not complete the requested command. Restart and retry.");
        return APPLICATION_ERROR;
    }
}
