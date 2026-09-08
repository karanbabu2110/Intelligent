package io.kaos.app;

import io.kaos.memory.AnswerDetail;
import io.kaos.memory.AnswerDetailMemory;
import io.kaos.memory.MemoryStorageException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Reports the presence and structured value of the fixed application memory. */
final class MemoryInspectCommand {
    private final CommandContext context;
    private final Supplier<Optional<AnswerDetail>> answerDetailLoader;

    MemoryInspectCommand(
            CommandContext context,
            Supplier<Optional<AnswerDetail>> answerDetailLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.answerDetailLoader = Objects.requireNonNull(
                answerDetailLoader, "answerDetailLoader");
    }

    int execute(String key) {
        if (!AnswerDetailMemory.KEY.equals(key)) {
            ErrorReporter.report(
                    context.errorOutput(), KaosApplication.MEMORY_INSPECTION_CODE,
                    "Only the answer-detail memory key can be inspected.");
            return KaosApplication.APPLICATION_ERROR;
        }
        try {
            Optional<AnswerDetail> answerDetail = answerDetailLoader.get();
            if (answerDetail.isEmpty()) {
                context.output().println("Memory absent: " + AnswerDetailMemory.KEY + ".");
            } else {
                context.output().println("Memory: " + AnswerDetailMemory.KEY + "="
                        + answerDetail.get().externalValue() + ".");
            }
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
