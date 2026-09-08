package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.memory.AnswerDetailMemory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MemoryDeleteCommandTest {
    @Test
    void confirmsDeletionAndReportsRepeatedDeletion() {
        Output output = new Output();
        AnswerDetailMemory memory = new AnswerDetailMemory();
        memory.create(AnswerDetailMemory.KEY, "balanced");
        MemoryDeleteCommand command = new MemoryDeleteCommand(output.context(), () -> memory);

        assertEquals(KaosApplication.SUCCESS, command.execute("answer-detail"));
        assertEquals(KaosApplication.APPLICATION_ERROR, command.execute("answer-detail"));
        assertEquals("Deleted memory: answer-detail." + System.lineSeparator(), output.standard());
        assertEquals("ERROR [KAOS-MEMORY-004] The answer-detail memory is absent; there is nothing to delete."
                + System.lineSeparator(), output.error());
    }

    private static final class Output {
        private final ByteArrayOutputStream standard = new ByteArrayOutputStream();
        private final ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context() {
            return new CommandContext(new ApplicationConfiguration("KAOS"),
                    InputStream.nullInputStream(),
                    new PrintStream(standard, true, StandardCharsets.UTF_8),
                    new PrintStream(error, true, StandardCharsets.UTF_8));
        }
        String standard() { return standard.toString(StandardCharsets.UTF_8); }
        String error() { return error.toString(StandardCharsets.UTF_8); }
    }
}
