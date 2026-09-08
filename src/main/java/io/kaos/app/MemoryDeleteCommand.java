package io.kaos.app;

import io.kaos.memory.AnswerDetailStore;
import io.kaos.memory.MemoryMutationException;
import io.kaos.memory.MemoryStorageException;
import java.util.Objects;
import java.util.function.Supplier;

/** Coordinates the explicit present-to-absent memory transition. */
final class MemoryDeleteCommand {
    private final CommandContext context;
    private final Supplier<AnswerDetailStore> storeFactory;

    MemoryDeleteCommand(CommandContext context, Supplier<AnswerDetailStore> storeFactory) {
        this.context = Objects.requireNonNull(context, "context");
        this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
    }

    int execute(String key) {
        try {
            storeFactory.get().delete(key);
            context.output().println("Deleted memory: answer-detail.");
            return KaosApplication.SUCCESS;
        } catch (MemoryMutationException exception) {
            String message = exception.reason() == MemoryMutationException.Reason.INVALID_KEY
                    ? "Only the answer-detail memory key can be deleted."
                    : "The answer-detail memory is absent; there is nothing to delete.";
            ErrorReporter.report(
                    context.errorOutput(), KaosApplication.MEMORY_MUTATION_CODE, message);
            return KaosApplication.APPLICATION_ERROR;
        } catch (MemoryStorageException exception) {
            ErrorReporter.report(context.errorOutput(), KaosApplication.MEMORY_STORAGE_CODE,
                    "The local memory database is unavailable or invalid. Check the configured data directory and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }
}
