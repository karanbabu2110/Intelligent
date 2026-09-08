package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.memory.AnswerDetailMemory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MemoryEditCommandTest {
    @Test
    void confirmsAnExplicitEdit() {
        Output output = new Output();
        AnswerDetailMemory memory = new AnswerDetailMemory();
        memory.create(AnswerDetailMemory.KEY, "concise");

        assertEquals(KaosApplication.SUCCESS,
                new MemoryEditCommand(output.context(), () -> memory)
                        .execute("answer-detail", "detailed"));
        assertEquals("Edited memory: answer-detail=detailed." + System.lineSeparator(),
                output.standard());
    }

    @Test
    void reportsAbsentAndInvalidValuesWithoutEchoingInput() {
        Output output = new Output();
        MemoryEditCommand command = new MemoryEditCommand(
                output.context(), AnswerDetailMemory::new);
        String privateValue = "private free-form value";

        assertEquals(KaosApplication.APPLICATION_ERROR,
                command.execute("answer-detail", privateValue));
        assertFalse(output.error().contains(privateValue));
        assertEquals("ERROR [KAOS-MEMORY-004] Answer detail must be concise, balanced, or detailed."
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
