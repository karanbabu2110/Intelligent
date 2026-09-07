package io.kaos.app;

import io.kaos.memory.AnswerDetail;
import io.kaos.memory.AnswerDetailMemory;
import io.kaos.memory.AnswerDetailStore;
import io.kaos.memory.MemoryCreationException;
import io.kaos.memory.MemoryStorageException;
import java.util.Objects;
import java.util.function.Supplier;

/** Coordinates the explicit absent-to-present memory transition. */
final class MemoryCreateCommand {
    private final CommandContext context;
    private final Supplier<AnswerDetailStore> storeLoader;

    MemoryCreateCommand(CommandContext context, AnswerDetailStore store) {
        this(context, () -> store);
    }

    MemoryCreateCommand(CommandContext context, Supplier<AnswerDetailStore> storeLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.storeLoader = Objects.requireNonNull(storeLoader, "storeLoader");
    }

    int execute(String key, String value) {
        try {
            AnswerDetail created = storeLoader.get().create(key, value);
            context.output().println("Created memory: " + AnswerDetailMemory.KEY
                    + "=" + created.externalValue() + ".");
            return KaosApplication.SUCCESS;
        } catch (MemoryCreationException exception) {
            return reportFailure(exception.reason());
        } catch (MemoryStorageException | IllegalArgumentException exception) {
            ErrorReporter.report(
                    context.errorOutput(), KaosApplication.MEMORY_STORAGE_CODE,
                    "The local memory database is unavailable or invalid. "
                            + "Check the configured data directory and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private int reportFailure(MemoryCreationException.Reason reason) {
        String message = switch (reason) {
            case INVALID_KEY -> "Only the answer-detail memory key can be created.";
            case INVALID_VALUE -> "Answer detail must be concise, balanced, or detailed.";
            case ALREADY_EXISTS -> "The answer-detail memory already exists; creation does not overwrite it.";
        };
        ErrorReporter.report(context.errorOutput(), KaosApplication.MEMORY_CREATION_CODE, message);
        return KaosApplication.APPLICATION_ERROR;
    }
}
