package io.kaos.app;

import io.kaos.memory.AnswerDetail;
import io.kaos.memory.AnswerDetailStore;
import io.kaos.memory.MemoryMutationException;
import io.kaos.memory.MemoryStorageException;
import java.util.Objects;
import java.util.function.Supplier;

/** Coordinates the explicit present-to-present memory transition. */
final class MemoryEditCommand {
    private final CommandContext context;
    private final Supplier<AnswerDetailStore> storeFactory;

    MemoryEditCommand(CommandContext context, Supplier<AnswerDetailStore> storeFactory) {
        this.context = Objects.requireNonNull(context, "context");
        this.storeFactory = Objects.requireNonNull(storeFactory, "storeFactory");
    }

    int execute(String key, String requestedValue) {
        try {
            AnswerDetail value = storeFactory.get().edit(key, requestedValue);
            context.output().println("Edited memory: answer-detail=" + value.externalValue() + ".");
            return KaosApplication.SUCCESS;
        } catch (MemoryMutationException exception) {
            ErrorReporter.report(context.errorOutput(), KaosApplication.MEMORY_MUTATION_CODE,
                    switch (exception.reason()) {
                        case INVALID_KEY -> "Only the answer-detail memory key can be edited.";
                        case INVALID_VALUE -> "Answer detail must be concise, balanced, or detailed.";
                        case ABSENT -> "The answer-detail memory is absent; create it before editing.";
                    });
            return KaosApplication.APPLICATION_ERROR;
        } catch (MemoryStorageException exception) {
            reportStorageFailure();
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private void reportStorageFailure() {
        ErrorReporter.report(context.errorOutput(), KaosApplication.MEMORY_STORAGE_CODE,
                "The local memory database is unavailable or invalid. Check the configured data directory and retry.");
    }
}
