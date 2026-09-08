package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaThinkingMode;
import io.kaos.conversation.ConversationHistory;
import io.kaos.memory.AnswerDetail;
import io.kaos.memory.MemoryStorageException;
import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Submits one prompt and presents its bounded streaming result. */
final class OllamaPromptCommand {
    private final CommandContext context;
    private final Supplier<OllamaModelConfiguration> modelConfigurationLoader;
    private final OllamaPromptSubmission promptSubmission;
    private final Supplier<Optional<AnswerDetail>> answerDetailLoader;

    OllamaPromptCommand(
            CommandContext context,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission) {
        this(context, modelConfigurationLoader, promptSubmission, Optional::empty);
    }

    OllamaPromptCommand(
            CommandContext context,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Optional<AnswerDetail>> answerDetailLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.modelConfigurationLoader = Objects.requireNonNull(
                modelConfigurationLoader, "modelConfigurationLoader");
        this.promptSubmission = Objects.requireNonNull(promptSubmission, "promptSubmission");
        this.answerDetailLoader = Objects.requireNonNull(
                answerDetailLoader, "answerDetailLoader");
    }

    int execute(String promptText) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(promptText);
        } catch (IllegalArgumentException exception) {
            reportInvalidPrompt();
            return KaosApplication.USAGE_ERROR;
        }
        try {
            Optional<AnswerDetail> answerDetail = answerDetailLoader.get();
            if (answerDetail.isPresent()) {
                prompt = new OllamaPrompt(prompt.text(), answerDetail.get().aiInstruction());
            }
        } catch (MemoryStorageException | IllegalArgumentException exception) {
            ErrorReporter.report(
                    context.errorOutput(), KaosApplication.MEMORY_STORAGE_CODE,
                    "The answer-detail memory could not be read. "
                            + "Check the configured memory data directory and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
        return submit(prompt, ConversationHistory.empty()).exitCode();
    }

    PromptOutcome submit(String promptText, ConversationHistory history) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(promptText);
        } catch (IllegalArgumentException exception) {
            reportInvalidPrompt();
            return PromptOutcome.failed(KaosApplication.USAGE_ERROR);
        }
        return submit(prompt, history);
    }

    private PromptOutcome submit(OllamaPrompt prompt, ConversationHistory history) {
        OllamaModelConfiguration model;
        try {
            model = modelConfigurationLoader.get();
        } catch (IllegalArgumentException exception) {
            ErrorReporter.report(
                    context.errorOutput(),
                    KaosApplication.INVALID_OLLAMA_MODEL_CODE,
                    "Invalid Ollama configuration. Check model, context-window, thinking, and "
                            + "response-token-limit process settings and retry.");
            return PromptOutcome.failed(KaosApplication.APPLICATION_ERROR);
        } catch (IllegalStateException exception) {
            ErrorReporter.report(
                    context.errorOutput(),
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
        String recovery = recovery(result.status());
        if (partialOutput) {
            recovery = "Partial streaming output was displayed before clean completion. "
                    + recovery;
        }
        ErrorReporter.report(context.errorOutput(), errorCode(result.status()), recovery);
        return PromptOutcome.failed(KaosApplication.APPLICATION_ERROR);
    }

    private void reportInvalidPrompt() {
        context.errorOutput().println(
                "Expected one valid quoted prompt. Run 'kaos help' for usage.");
    }

    private static String recovery(OllamaPromptClient.Status status) {
        return switch (status) {
            case TOKEN_LIMIT_REACHED ->
                    "Ollama reached a response or context length boundary before completing the "
                            + "answer. Review the response-token limit and context window, then retry.";
            case LOCAL_LIMIT_REACHED ->
                    "KAOS stopped the Ollama stream at a local byte or text safety limit. "
                            + "Shorten the request or response, then retry.";
            case UNAVAILABLE -> "Local Ollama could not be reached before the prompt response began. "
                    + "Start Ollama on 127.0.0.1:11434 and retry.";
            case REQUEST_FAILED ->
                    "Local Ollama rejected the prompt request. Verify the configured model and retry.";
            case INVALID_RESPONSE ->
                    "Local Ollama returned an invalid prompt response. Verify Ollama and retry.";
            case STREAM_FAILED -> "The accepted Ollama response stream lost its local connection. "
                    + "Verify Ollama is still running, then retry.";
            case TOTAL_TIMEOUT -> "The Ollama prompt exceeded its five-minute total deadline. "
                    + "Shorten the request or select a faster local model, then retry.";
            case INACTIVITY_TIMEOUT -> "The Ollama response stream produced no data for 60 seconds. "
                    + "Verify Ollama is still progressing or select a faster local model, then retry.";
            case INTERRUPTED -> "The Ollama prompt request was cancelled. Retry when ready.";
            case SUCCESS -> throw new IllegalStateException("Successful result has no recovery.");
        };
    }

    private static String errorCode(OllamaPromptClient.Status status) {
        return switch (status) {
            case UNAVAILABLE -> KaosApplication.OLLAMA_CONNECTIVITY_CODE;
            case REQUEST_FAILED, INVALID_RESPONSE -> KaosApplication.OLLAMA_PROMPT_CODE;
            case TOKEN_LIMIT_REACHED, LOCAL_LIMIT_REACHED ->
                    KaosApplication.OLLAMA_RESPONSE_LIMIT_CODE;
            case STREAM_FAILED -> KaosApplication.OLLAMA_STREAM_CODE;
            case TOTAL_TIMEOUT, INACTIVITY_TIMEOUT -> KaosApplication.OLLAMA_TIMEOUT_CODE;
            case INTERRUPTED -> KaosApplication.OLLAMA_CANCELLATION_CODE;
            case SUCCESS -> throw new IllegalStateException("Successful result has no error code.");
        };
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
