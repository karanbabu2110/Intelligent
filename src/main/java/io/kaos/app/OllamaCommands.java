package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaThinkingMode;
import io.kaos.conversation.ConversationHistory;
import java.io.PrintStream;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Coordinates Ollama-facing application commands and their terminal presentation.
 */
final class OllamaCommands {
    private final CommandContext context;
    private final Supplier<OllamaConnectivity.Result> connectivityCheck;
    private final Supplier<OllamaModelConfiguration> modelConfigurationLoader;
    private final OllamaPromptSubmission promptSubmission;

    OllamaCommands(
            CommandContext context,
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission) {
        this.context = Objects.requireNonNull(context, "context");
        this.connectivityCheck = Objects.requireNonNull(connectivityCheck, "connectivityCheck");
        this.modelConfigurationLoader = Objects.requireNonNull(
                modelConfigurationLoader, "modelConfigurationLoader");
        this.promptSubmission = Objects.requireNonNull(promptSubmission, "promptSubmission");
    }

    int reportStatus() {
        OllamaConnectivity.Result result = connectivityCheck.get();
        Objects.requireNonNull(result, "result");
        if (result.reachable()) {
            context.output().println(
                    "Local Ollama is reachable (version " + result.version() + ").");
            return KaosApplication.SUCCESS;
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
        logError(KaosApplication.OLLAMA_CONNECTIVITY_CODE, recovery);
        return KaosApplication.APPLICATION_ERROR;
    }

    int reportModel() {
        try {
            OllamaModelConfiguration configuration = modelConfigurationLoader.get();
            context.output().println("Configured local Ollama model: " + configuration.modelName()
                    + " (context window: " + configuration.contextWindow()
                    + " tokens, thinking: "
                    + configuration.thinkingMode().configurationValue()
                    + ", response limit: " + configuration.responseTokenLimit()
                    + " tokens).");
            return KaosApplication.SUCCESS;
        } catch (IllegalArgumentException exception) {
            logError(
                    KaosApplication.INVALID_OLLAMA_MODEL_CODE,
                    invalidConfigurationGuidance());
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(
                    KaosApplication.UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    int reportPrompt(String promptText) {
        return submitPrompt(promptText, ConversationHistory.empty()).exitCode();
    }

    PromptOutcome submitPrompt(String promptText, ConversationHistory history) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(promptText);
        } catch (IllegalArgumentException exception) {
            context.errorOutput().println(
                    "Expected one valid quoted prompt. Run 'kaos help' for usage.");
            return PromptOutcome.failed(KaosApplication.USAGE_ERROR);
        }

        OllamaModelConfiguration model;
        try {
            model = modelConfigurationLoader.get();
        } catch (IllegalArgumentException exception) {
            logError(
                    KaosApplication.INVALID_OLLAMA_MODEL_CODE,
                    invalidConfigurationGuidance());
            return PromptOutcome.failed(KaosApplication.APPLICATION_ERROR);
        } catch (IllegalStateException exception) {
            logError(
                    KaosApplication.UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return PromptOutcome.failed(KaosApplication.APPLICATION_ERROR);
        }

        PromptOutput promptOutput = new PromptOutput(model.thinkingMode(), context.output());
        OllamaPromptClient.Result result = promptSubmission.submit(
                model, history, prompt, promptOutput::thinkingStarted, promptOutput::answerChunk);
        if (result.successful()) {
            context.output().println();
            return PromptOutcome.success(prompt.text(), result.response());
        }

        boolean partialOutput = promptOutput.finishFailure();
        String recovery = switch (result.status()) {
            case TOKEN_LIMIT_REACHED ->
                    "Ollama reached a response or context length boundary before completing the "
                            + "answer. Review the response-token limit and context window, then "
                            + "retry.";
            case LOCAL_LIMIT_REACHED ->
                    "KAOS stopped the Ollama stream at a local byte or text safety limit. "
                            + "Shorten the request or response, then retry.";
            case UNAVAILABLE ->
                    "Local Ollama could not be reached before the prompt response began. "
                            + "Start Ollama on 127.0.0.1:11434 and retry.";
            case REQUEST_FAILED ->
                    "Local Ollama rejected the prompt request. Verify the configured model and retry.";
            case INVALID_RESPONSE ->
                    "Local Ollama returned an invalid prompt response. Verify Ollama and retry.";
            case STREAM_FAILED ->
                    "The accepted Ollama response stream lost its local connection. "
                            + "Verify Ollama is still running, then retry.";
            case TOTAL_TIMEOUT ->
                    "The Ollama prompt exceeded its five-minute total deadline. Shorten the "
                            + "request or select a faster local model, then retry.";
            case INACTIVITY_TIMEOUT ->
                    "The Ollama response stream produced no data for 60 seconds. Verify Ollama "
                            + "is still progressing or select a faster local model, then retry.";
            case INTERRUPTED ->
                    "The Ollama prompt request was cancelled. Retry when ready.";
            case SUCCESS -> throw new IllegalStateException("Successful result has no response.");
        };
        if (partialOutput) {
            recovery = "Partial streaming output was displayed before clean completion. "
                    + recovery;
        }
        String errorCode = switch (result.status()) {
            case UNAVAILABLE -> KaosApplication.OLLAMA_CONNECTIVITY_CODE;
            case REQUEST_FAILED, INVALID_RESPONSE -> KaosApplication.OLLAMA_PROMPT_CODE;
            case TOKEN_LIMIT_REACHED, LOCAL_LIMIT_REACHED ->
                    KaosApplication.OLLAMA_RESPONSE_LIMIT_CODE;
            case STREAM_FAILED -> KaosApplication.OLLAMA_STREAM_CODE;
            case TOTAL_TIMEOUT, INACTIVITY_TIMEOUT -> KaosApplication.OLLAMA_TIMEOUT_CODE;
            case INTERRUPTED -> KaosApplication.OLLAMA_CANCELLATION_CODE;
            case SUCCESS -> throw new IllegalStateException("Successful result has no error code.");
        };
        logError(errorCode, recovery);
        return PromptOutcome.failed(KaosApplication.APPLICATION_ERROR);
    }

    private static String invalidConfigurationGuidance() {
        return "Invalid Ollama configuration. Check model, context-window, thinking, and "
                + "response-token-limit process settings and retry.";
    }

    private void logError(String code, String message) {
        context.errorOutput().println("ERROR [" + code + "] " + message);
    }

    record PromptOutcome(int exitCode, String prompt, String response) {
        PromptOutcome {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(response, "response");
        }

        private static PromptOutcome success(String prompt, String response) {
            return new PromptOutcome(KaosApplication.SUCCESS, prompt, response);
        }

        private static PromptOutcome failed(int exitCode) {
            return new PromptOutcome(exitCode, "", "");
        }
    }

    private static final class PromptOutput {
        private final OllamaThinkingMode thinkingMode;
        private final PrintStream output;
        private boolean thinkingVisible;
        private boolean answerVisible;
        private boolean answerContentVisible;
        private boolean answerEndsWithLineBreak;

        private PromptOutput(OllamaThinkingMode thinkingMode, PrintStream output) {
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
            if (!chunk.isEmpty()) {
                answerContentVisible = true;
                answerEndsWithLineBreak = chunk.endsWith("\n") || chunk.endsWith("\r");
            }
        }

        private boolean finishFailure() {
            if (answerContentVisible && !answerEndsWithLineBreak) {
                output.println();
                output.flush();
            }
            return thinkingVisible || answerContentVisible;
        }
    }
}
