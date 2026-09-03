package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import java.util.Objects;
import java.util.function.Supplier;

/** Loads and reports the selected local Ollama model configuration. */
final class OllamaModelCommand {
    private final CommandContext context;
    private final Supplier<OllamaModelConfiguration> configurationLoader;

    OllamaModelCommand(
            CommandContext context,
            Supplier<OllamaModelConfiguration> configurationLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.configurationLoader = Objects.requireNonNull(
                configurationLoader, "configurationLoader");
    }

    int execute() {
        try {
            OllamaModelConfiguration configuration = configurationLoader.get();
            context.output().println("Configured local Ollama model: " + configuration.modelName()
                    + " (context window: " + configuration.contextWindow()
                    + " tokens, thinking: "
                    + configuration.thinkingMode().configurationValue()
                    + ", response limit: " + configuration.responseTokenLimit()
                    + " tokens).");
            return KaosApplication.SUCCESS;
        } catch (IllegalArgumentException exception) {
            reportInvalidConfiguration();
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            ErrorReporter.report(
                    context.errorOutput(),
                    KaosApplication.UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private void reportInvalidConfiguration() {
        ErrorReporter.report(
                context.errorOutput(),
                KaosApplication.INVALID_OLLAMA_MODEL_CODE,
                "Invalid Ollama configuration. Check model, context-window, thinking, and "
                        + "response-token-limit process settings and retry.");
    }
}
