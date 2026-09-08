package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.memory.AnswerDetail;
import io.kaos.memory.MemoryStorageException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemoryInspectCommandTest {
    @Test
    void reportsTheValidatedStructuredValue() {
        Output output = new Output();
        MemoryInspectCommand command = new MemoryInspectCommand(
                output.context(), () -> Optional.of(AnswerDetail.BALANCED));

        assertEquals(KaosApplication.SUCCESS, command.execute("answer-detail"));
        assertEquals("Memory: answer-detail=balanced." + System.lineSeparator(),
                output.standard());
        assertEquals("", output.error());
    }

    @Test
    void reportsAbsenceAsSuccessfulInspection() {
        Output output = new Output();
        MemoryInspectCommand command = new MemoryInspectCommand(
                output.context(), Optional::empty);

        assertEquals(KaosApplication.SUCCESS, command.execute("answer-detail"));
        assertEquals("Memory absent: answer-detail." + System.lineSeparator(),
                output.standard());
        assertEquals("", output.error());
    }

    @Test
    void rejectsAnUnsupportedKeyBeforeLoadingStorage() {
        Output output = new Output();
        AtomicInteger loads = new AtomicInteger();
        MemoryInspectCommand command = new MemoryInspectCommand(
                output.context(), () -> {
                    loads.incrementAndGet();
                    return Optional.empty();
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, command.execute("private-key"));
        assertEquals(0, loads.get());
        assertEquals("", output.standard());
        assertEquals(
                "ERROR [KAOS-MEMORY-003] Only the answer-detail memory key can be inspected."
                        + System.lineSeparator(),
                output.error());
        assertFalse(output.error().contains("private-key"));
    }

    @Test
    void reportsStorageFailureWithoutExposingTheKeyOrCause() {
        Output output = new Output();
        MemoryInspectCommand command = new MemoryInspectCommand(
                output.context(), () -> {
                    throw MemoryStorageException.unavailable();
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, command.execute("answer-detail"));
        assertEquals("", output.standard());
        assertEquals(
                "ERROR [KAOS-MEMORY-002] The local memory database is unavailable or invalid. "
                        + "Check the configured data directory and retry."
                        + System.lineSeparator(),
                output.error());
    }

    private static final class Output {
        private final ByteArrayOutputStream standard = new ByteArrayOutputStream();
        private final ByteArrayOutputStream error = new ByteArrayOutputStream();

        CommandContext context() {
            return new CommandContext(
                    new ApplicationConfiguration("KAOS"), InputStream.nullInputStream(),
                    new PrintStream(standard, true, StandardCharsets.UTF_8),
                    new PrintStream(error, true, StandardCharsets.UTF_8));
        }

        String standard() {
            return standard.toString(StandardCharsets.UTF_8);
        }

        String error() {
            return error.toString(StandardCharsets.UTF_8);
        }
    }
}
