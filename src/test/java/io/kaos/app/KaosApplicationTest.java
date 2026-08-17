package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KaosApplicationTest {
    @Test
    void identifiesTheApplicationBaselineWithoutClaimingAProductCapability() {
        assertEquals("KAOS application baseline is running.", KaosApplication.startupMessage());
    }

    @Test
    void startsThroughTheSelectedEntryPointAndReturnsAfterItsDiagnostic() {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();

        try (PrintStream testOutput = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8)) {
            System.setOut(testOutput);
            KaosApplication.main(new String[0]);
        } finally {
            System.setOut(originalOutput);
        }

        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                capturedOutput.toString(StandardCharsets.UTF_8));
    }
}
