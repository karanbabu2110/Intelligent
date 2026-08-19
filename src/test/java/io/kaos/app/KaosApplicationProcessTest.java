package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class KaosApplicationProcessTest {
    @Test
    void runsTheRealEntryPointWithIsolatedDefaultConfiguration() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.process("status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void suppliesExplicitConfigurationToTheRealEntryPoint() {
        KaosApplicationHarness.Result result =
                KaosApplicationHarness.processWithApplicationName("Local KAOS", "status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Local KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void capturesARealUsageFailureWithoutEchoingTheArgument() {
        String privateArgument = "private-process-argument";

        KaosApplicationHarness.Result result = KaosApplicationHarness.process(privateArgument);

        assertEquals(KaosApplication.USAGE_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "Unknown command. Run 'kaos help' for usage." + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateArgument));
    }

    @Test
    void capturesARealConfigurationFailureWithoutEchoingTheValue() {
        String privateConfiguration = "private-process-configuration!";

        KaosApplicationHarness.Result result =
                KaosApplicationHarness.processWithApplicationName(privateConfiguration, "status");

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-CONFIG-001] Invalid application configuration. "
                        + "Check kaos.app.name or KAOS_APP_NAME and restart."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateConfiguration));
    }

    @Test
    void terminatesAChildTestProcessThatExceedsItsTimeout() {
        AssertionError failure = assertThrows(
                AssertionError.class,
                () -> KaosApplicationHarness.processMain(
                        HangingProcessFixture.class,
                        Duration.ofMillis(250),
                        null));

        assertTrue(failure.getMessage().contains("exceeded 250 ms"));
        assertTrue(failure.getMessage().contains("HangingProcessFixture"));
    }

    public static final class HangingProcessFixture {
        private HangingProcessFixture() {
        }

        public static void main(String[] arguments) throws InterruptedException {
            Thread.sleep(Duration.ofSeconds(30));
        }
    }
}
