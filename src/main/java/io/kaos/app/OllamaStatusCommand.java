package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import java.util.Objects;
import java.util.function.Supplier;

/** Reports connectivity to the fixed local Ollama endpoint. */
final class OllamaStatusCommand {
    private final CommandContext context;
    private final Supplier<OllamaConnectivity.Result> connectivityCheck;

    OllamaStatusCommand(
            CommandContext context,
            Supplier<OllamaConnectivity.Result> connectivityCheck) {
        this.context = Objects.requireNonNull(context, "context");
        this.connectivityCheck = Objects.requireNonNull(connectivityCheck, "connectivityCheck");
    }

    int execute() {
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
        ErrorReporter.report(
                context.errorOutput(), KaosApplication.OLLAMA_CONNECTIVITY_CODE, recovery);
        return KaosApplication.APPLICATION_ERROR;
    }
}
