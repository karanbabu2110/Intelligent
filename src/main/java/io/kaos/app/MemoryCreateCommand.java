package io.kaos.app;

import io.kaos.memory.AnswerDetail;
import io.kaos.memory.AnswerDetailMemory;
import io.kaos.memory.MemoryCreationException;
import java.util.Objects;

/** Coordinates the explicit absent-to-present memory transition. */
final class MemoryCreateCommand {
    private final CommandContext context;
    private final AnswerDetailMemory memory;

    MemoryCreateCommand(CommandContext context, AnswerDetailMemory memory) {
        this.context = Objects.requireNonNull(context, "context");
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    int execute(String key, String value) {
        try {
            AnswerDetail created = memory.create(key, value);
            context.output().println("Created memory: " + AnswerDetailMemory.KEY
                    + "=" + created.externalValue() + ".");
            return KaosApplication.SUCCESS;
        } catch (MemoryCreationException exception) {
            return reportFailure(exception.reason());
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
