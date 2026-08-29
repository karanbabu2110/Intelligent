package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaThinkingMode;
import io.kaos.app.config.ApplicationConfiguration;
import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Consumer;
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
                (model, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(model, prompt, thinking, chunks),
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
                (model, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(model, prompt, thinking, chunks),
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
        return run(
                arguments,
                configuration,
                ollamaConnectivityCheck,
                ollamaModelConfigurationLoader,
                (model, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(model, prompt, thinking, chunks),
                output,
                errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            Supplier<OllamaModelConfiguration> ollamaModelConfigurationLoader,
            OllamaPromptSubmission ollamaPromptSubmission,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(ollamaConnectivityCheck, "ollamaConnectivityCheck");
        Objects.requireNonNull(ollamaModelConfigurationLoader, "ollamaModelConfigurationLoader");
        Objects.requireNonNull(ollamaPromptSubmission, "ollamaPromptSubmission");
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

        if (arguments.length == 2 && "ollama-prompt".equals(arguments[0])) {
            return reportOllamaPrompt(
                    arguments[1],
                    ollamaModelConfigurationLoader,
                    ollamaPromptSubmission,
                    output,
                    errorOutput);
        }

        if (arguments.length > 0 && "ollama-prompt".equals(arguments[0])) {
            errorOutput.println("Expected one quoted prompt. Run 'kaos help' for usage.");
            return USAGE_ERROR;
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
                Usage: kaos [status|help|ollama-status|ollama-model|ollama-prompt <prompt>]

                Commands:
                  status         Show local application status (default).
                  help           Show this help. The --help alias is also supported.
                  ollama-status  Check connectivity to the local Ollama server.
                  ollama-model   Show the explicitly configured local Ollama model.
                  ollama-prompt  Submit one quoted prompt and stream the answer.
                """;
    }

    private static int reportOllamaPrompt(
            String promptText,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            PrintStream output,
            PrintStream errorOutput) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(promptText);
        } catch (IllegalArgumentException exception) {
            errorOutput.println("Expected one valid quoted prompt. Run 'kaos help' for usage.");
            return USAGE_ERROR;
        }

        OllamaModelConfiguration model;
        try {
            model = modelConfigurationLoader.get();
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_OLLAMA_MODEL_CODE,
                    invalidOllamaConfigurationGuidance());
            return APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(
                    errorOutput,
                    UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return APPLICATION_ERROR;
        }

        OllamaPromptOutput promptOutput = new OllamaPromptOutput(model.thinkingMode(), output);
        OllamaPromptClient.Result result = promptSubmission.submit(
                model, prompt, promptOutput::thinkingStarted, promptOutput::answerChunk);
        if (result.successful()) {
            output.println();
            return SUCCESS;
        }

        String recovery = switch (result.status()) {
            case TOKEN_LIMIT_REACHED ->
                    "Ollama reached a response or context length boundary before completing the "
                            + "answer. Review the response-token limit and context window, then "
                            + "retry.";
            case UNAVAILABLE ->
                    "Local Ollama is unavailable. Start Ollama on 127.0.0.1:11434 and retry.";
            case REQUEST_FAILED ->
                    "Local Ollama rejected the prompt request. Verify the configured model and retry.";
            case INVALID_RESPONSE ->
                    "Local Ollama returned an invalid prompt response. Verify Ollama and retry.";
            case TIMED_OUT ->
                    "The Ollama prompt request timed out. Try again or select a faster local model.";
            case INTERRUPTED ->
                    "The Ollama prompt request was interrupted. Retry the command.";
            case SUCCESS -> throw new IllegalStateException("Successful result has no response.");
        };
        String errorCode = result.status() == OllamaPromptClient.Status.TOKEN_LIMIT_REACHED
                ? OLLAMA_RESPONSE_LIMIT_CODE
                : OLLAMA_PROMPT_CODE;
        logError(errorOutput, errorCode, recovery);
        return APPLICATION_ERROR;
    }

    @FunctionalInterface
    interface OllamaPromptSubmission {
        OllamaPromptClient.Result submit(
                OllamaModelConfiguration model,
                OllamaPrompt prompt,
                Runnable thinkingStarted,
                Consumer<String> answerChunkConsumer);
    }

    private static final class OllamaPromptOutput {
        private final OllamaThinkingMode thinkingMode;
        private final PrintStream output;
        private boolean thinkingVisible;
        private boolean answerVisible;

        private OllamaPromptOutput(OllamaThinkingMode thinkingMode, PrintStream output) {
            this.thinkingMode = Objects.requireNonNull(thinkingMode, "thinkingMode");
            this.output = Objects.requireNonNull(output, "output");
        }

        private void thinkingStarted() {
            if (thinkingMode == OllamaThinkingMode.ON && !thinkingVisible) {
                output.println("Thinking...");
                output.flush();
                thinkingVisible = true;
            }
        }

        private void answerChunk(String chunk) {
            if (thinkingMode == OllamaThinkingMode.ON && !answerVisible) {
                output.println("Answer:");
                answerVisible = true;
            }
            output.print(chunk);
            output.flush();
        }
    }

    private static int reportOllamaModel(
            Supplier<OllamaModelConfiguration> configurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        try {
            OllamaModelConfiguration configuration = configurationLoader.get();
            output.println("Configured local Ollama model: " + configuration.modelName()
                    + " (context window: " + configuration.contextWindow()
                    + " tokens, thinking: "
                    + configuration.thinkingMode().configurationValue()
                    + ", response limit: " + configuration.responseTokenLimit()
                    + " tokens).");
            return SUCCESS;
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_OLLAMA_MODEL_CODE,
                    invalidOllamaConfigurationGuidance());
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

    private static String invalidOllamaConfigurationGuidance() {
        return "Invalid Ollama configuration. Check model, context-window, thinking, and "
                + "response-token-limit process settings and retry.";
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
