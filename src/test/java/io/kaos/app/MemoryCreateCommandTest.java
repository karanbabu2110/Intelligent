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

class MemoryCreateCommandTest {
    @Test
    void confirmsExplicitCreation() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        MemoryCreateCommand command = command(output, errorOutput, new AnswerDetailMemory());

        int exitCode = command.execute("answer-detail", "balanced");

        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertEquals("Created memory: answer-detail=balanced." + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8));
        assertEquals("", errorOutput.toString(StandardCharsets.UTF_8));
    }

    @Test
    void reportsInvalidInputWithoutEchoingIt() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        MemoryCreateCommand command = command(output, errorOutput, new AnswerDetailMemory());
        String privateValue = "private free-form preference";

        int exitCode = command.execute("answer-detail", privateValue);

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals(
                "ERROR [KAOS-MEMORY-001] Answer detail must be concise, balanced, or detailed."
                        + System.lineSeparator(),
                errorOutput.toString(StandardCharsets.UTF_8));
        assertFalse(errorOutput.toString(StandardCharsets.UTF_8).contains(privateValue));
    }

    @Test
    void reportsDuplicateCreationWithoutOverwriting() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        MemoryCreateCommand command = command(output, errorOutput, new AnswerDetailMemory());
        assertEquals(KaosApplication.SUCCESS, command.execute("answer-detail", "concise"));

        int exitCode = command.execute("answer-detail", "detailed");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals(
                "ERROR [KAOS-MEMORY-001] The answer-detail memory already exists; creation does not overwrite it."
                        + System.lineSeparator(),
                errorOutput.toString(StandardCharsets.UTF_8));
    }

    private static MemoryCreateCommand command(
            ByteArrayOutputStream output,
            ByteArrayOutputStream errorOutput,
            AnswerDetailMemory memory) {
        CommandContext context = new CommandContext(
                new ApplicationConfiguration("KAOS"), InputStream.nullInputStream(),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
        return new MemoryCreateCommand(context, memory);
    }
}
