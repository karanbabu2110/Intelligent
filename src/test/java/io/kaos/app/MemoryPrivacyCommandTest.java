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
import org.junit.jupiter.api.Test;

class MemoryPrivacyCommandTest {
    @Test
    void reportsAbsentAndPresentPolicyWithoutTheStoredValue() {
        Output absent = new Output();
        Output present = new Output();

        assertEquals(KaosApplication.SUCCESS,
                new MemoryPrivacyCommand(absent.context(), Optional::empty).execute());
        assertEquals(KaosApplication.SUCCESS,
                new MemoryPrivacyCommand(
                        present.context(), () -> Optional.of(AnswerDetail.DETAILED)).execute());

        assertEquals(report("absent"), absent.standard());
        assertEquals(report("present"), present.standard());
        assertFalse(present.standard().contains("detailed"));
        assertEquals("", present.error());
    }

    @Test
    void failsSafelyWhenPresenceCannotBeValidated() {
        Output output = new Output();
        MemoryPrivacyCommand command = new MemoryPrivacyCommand(output.context(), () -> {
            throw MemoryStorageException.unavailable();
        });

        assertEquals(KaosApplication.APPLICATION_ERROR, command.execute());
        assertEquals("", output.standard());
        assertEquals("ERROR [KAOS-MEMORY-002] The local memory database is unavailable or invalid. "
                + "Check the configured data directory and retry." + System.lineSeparator(),
                output.error());
    }

    private static String report(String state) {
        return "Memory privacy: collection=explicit-only; storage=local-only; "
                + "ai-use=ollama-prompt-only; state=" + state + "." + System.lineSeparator();
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

        String standard() {
            return standard.toString(StandardCharsets.UTF_8);
        }

        String error() {
            return error.toString(StandardCharsets.UTF_8);
        }
    }
}
