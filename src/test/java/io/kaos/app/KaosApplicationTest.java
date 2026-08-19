package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.OutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class KaosApplicationTest {
    @Test
    void identifiesTheApplicationBaselineWithoutClaimingAProductCapability() {
        assertEquals(
                "KAOS application baseline is running.",
                KaosApplication.startupMessage(
                        new ApplicationConfiguration(
                                ApplicationConfiguration.DEFAULT_APPLICATION_NAME)));
    }

    @Test
    void startsThroughTheSelectedEntryPointAndReturnsAfterItsDiagnostic() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.run();

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(), result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void reportsStatusForTheExplicitStatusCommand() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.run("status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void printsHelpForTheHelpCommand() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.run("help");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(KaosApplication.helpText(), result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void printsHelpForTheLongHelpAlias() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.run("--help");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(KaosApplication.helpText(), result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void usesTheConfiguredApplicationNameInTheStartupDiagnostic() {
        assertEquals(
                "Local KAOS application baseline is running.",
                KaosApplication.startupMessage(new ApplicationConfiguration("Local KAOS")));
    }

    @Test
    void usesTheConfiguredApplicationNameForTheStatusCommand() {
        KaosApplicationHarness.Result result =
                KaosApplicationHarness.run(new ApplicationConfiguration("Local KAOS"), "status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Local KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void rejectsAnUnknownCommandWithoutEchoingIt() {
        String untrustedCommand = "unknown-token-value";

        KaosApplicationHarness.Result result = KaosApplicationHarness.run(untrustedCommand);

        assertEquals(KaosApplication.USAGE_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "Unknown command. Run 'kaos help' for usage." + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(untrustedCommand));
    }

    @Test
    void rejectsExtraArgumentsWithoutEchoingThem() {
        String untrustedArgument = "private-token-value";

        KaosApplicationHarness.Result result =
                KaosApplicationHarness.run("status", untrustedArgument);

        assertEquals(KaosApplication.USAGE_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "Expected at most one command. Run 'kaos help' for usage." + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(untrustedArgument));
    }

    @Test
    void launchesAValidCommandThroughTheApplicationFailureBoundary() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.launch(
                () -> new ApplicationConfiguration(
                        ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                "status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void logsInvalidConfigurationWithoutDisclosingExceptionDetails() {
        String privateDetail = "private-invalid-value";

        KaosApplicationHarness.Result result = KaosApplicationHarness.launch(
                () -> {
                    throw new IllegalArgumentException(privateDetail);
                },
                "status");

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-CONFIG-001] Invalid application configuration. "
                        + "Check kaos.app.name or KAOS_APP_NAME and restart."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateDetail));
    }

    @Test
    void logsUnreadableConfigurationWithoutDisclosingExceptionDetails() {
        String privateDetail = "private-permission-detail";

        KaosApplicationHarness.Result result = KaosApplicationHarness.launch(
                () -> {
                    throw new IllegalStateException(privateDetail);
                },
                "status");

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-CONFIG-002] Local application configuration could not be read. "
                        + "Check process permissions and restart."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateDetail));
    }

    @Test
    void logsUnexpectedConfigurationLoaderFailureWithoutDisclosingExceptionDetails() {
        String privateDetail = "private-runtime-detail";

        KaosApplicationHarness.Result result = KaosApplicationHarness.launch(
                () -> {
                    throw new UnsupportedOperationException(privateDetail);
                },
                "status");

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-APP-001] KAOS could not complete the requested command. "
                        + "Restart and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateDetail));
    }

    @Test
    void logsUnexpectedCommandFailureWithoutMisclassifyingItAsConfiguration() {
        String privateDetail = "private-command-detail";

        try (PrintStream failingOutput = new PrintStream(OutputStream.nullOutputStream()) {
                    @Override
                    public void println(String value) {
                        throw new UnsupportedOperationException(privateDetail);
                    }
                }) {
            KaosApplicationHarness.Result result = KaosApplicationHarness.capture(
                    (output, errorOutput) -> KaosApplication.launch(
                            new String[] {"status"},
                            () -> new ApplicationConfiguration(
                                    ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                            failingOutput,
                            errorOutput));

            assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
            assertEquals("", result.standardOutput());
            assertEquals(
                    "ERROR [KAOS-APP-001] KAOS could not complete the requested command. "
                            + "Restart and retry."
                            + System.lineSeparator(),
                    result.errorOutput());
            assertFalse(result.errorOutput().contains(privateDetail));
            assertFalse(result.errorOutput().contains(KaosApplication.INVALID_CONFIGURATION_CODE));
            assertFalse(result.errorOutput().contains(KaosApplication.UNREADABLE_CONFIGURATION_CODE));
        }
    }

    @Test
    void doesNotConvertJvmErrorsIntoApplicationFailures() {
        AssertionError failure = new AssertionError("fatal-test-condition");

        AssertionError thrown = assertThrows(
                AssertionError.class,
                () -> KaosApplicationHarness.launch(
                        () -> {
                            throw failure;
                        },
                        "status"));

        assertEquals(failure, thrown);
    }
}
