package io.kaos.app;

import io.kaos.memory.AnswerDetail;
import io.kaos.memory.MemoryStorageException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Reports the bounded collection, storage, use, and presence policy for memory. */
final class MemoryPrivacyCommand {
    private final CommandContext context;
    private final Supplier<Optional<AnswerDetail>> answerDetailLoader;

    MemoryPrivacyCommand(
            CommandContext context,
            Supplier<Optional<AnswerDetail>> answerDetailLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.answerDetailLoader = Objects.requireNonNull(
                answerDetailLoader, "answerDetailLoader");
    }

    int execute() {
        try {
            String state = answerDetailLoader.get().isPresent() ? "present" : "absent";
            context.output().println("Memory privacy: collection=explicit-only; "
                    + "storage=local-only; ai-use=ollama-prompt-only; state=" + state + ".");
            return KaosApplication.SUCCESS;
        } catch (MemoryStorageException | IllegalArgumentException exception) {
            ErrorReporter.report(
                    context.errorOutput(), KaosApplication.MEMORY_STORAGE_CODE,
                    "The local memory database is unavailable or invalid. "
                            + "Check the configured data directory and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }
}
