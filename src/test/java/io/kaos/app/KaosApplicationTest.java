package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
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
        CommandResult result = run();

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(), result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void reportsStatusForTheExplicitStatusCommand() {
        CommandResult result = run("status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void printsHelpForTheHelpCommand() {
        CommandResult result = run("help");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(KaosApplication.helpText(), result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void printsHelpForTheLongHelpAlias() {
        CommandResult result = run("--help");

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
        CommandResult result = run(new ApplicationConfiguration("Local KAOS"), "status");

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Local KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void rejectsAnUnknownCommandWithoutEchoingIt() {
        String untrustedCommand = "unknown-token-value";

        CommandResult result = run(untrustedCommand);

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

        CommandResult result = run("status", untrustedArgument);

        assertEquals(KaosApplication.USAGE_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "Expected at most one command. Run 'kaos help' for usage." + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(untrustedArgument));
    }

    @Test
    void launchesAValidCommandThroughTheApplicationFailureBoundary() {
        CommandResult result = launch(
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

        CommandResult result = launch(
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

        CommandResult result = launch(
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

        CommandResult result = launch(
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
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

        try (PrintStream failingOutput = new PrintStream(OutputStream.nullOutputStream()) {
                    @Override
                    public void println(String value) {
                        throw new UnsupportedOperationException(privateDetail);
                    }
                };
                PrintStream testError =
                        new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            int exitCode = KaosApplication.launch(
                    new String[] {"status"},
                    () -> new ApplicationConfiguration(
                            ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                    failingOutput,
                    testError);

            String errorOutput = capturedError.toString(StandardCharsets.UTF_8);
            assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
            assertEquals(
                    "ERROR [KAOS-APP-001] KAOS could not complete the requested command. "
                            + "Restart and retry."
                            + System.lineSeparator(),
                    errorOutput);
            assertFalse(errorOutput.contains(privateDetail));
            assertFalse(errorOutput.contains(KaosApplication.INVALID_CONFIGURATION_CODE));
            assertFalse(errorOutput.contains(KaosApplication.UNREADABLE_CONFIGURATION_CODE));
        }
    }

    @Test
    void doesNotConvertJvmErrorsIntoApplicationFailures() {
        AssertionError failure = new AssertionError("fatal-test-condition");

        AssertionError thrown = assertThrows(
                AssertionError.class,
                () -> launch(
                        () -> {
                            throw failure;
                        },
                        "status"));

        assertEquals(failure, thrown);
    }

    private static CommandResult run(String... arguments) {
        return run(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                arguments);
    }

    private static CommandResult run(
            ApplicationConfiguration configuration, String... arguments) {
        return capture((output, errorOutput) ->
                KaosApplication.run(arguments, configuration, output, errorOutput));
    }

    private static CommandResult launch(
            Supplier<ApplicationConfiguration> configurationLoader, String... arguments) {
        return capture((output, errorOutput) ->
                KaosApplication.launch(arguments, configurationLoader, output, errorOutput));
    }

    private static CommandResult capture(CommandInvocation invocation) {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

        try (PrintStream testOutput =
                        new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
                PrintStream testError =
                        new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            int exitCode = invocation.invoke(testOutput, testError);
            return new CommandResult(
                    exitCode,
                    capturedOutput.toString(StandardCharsets.UTF_8),
                    capturedError.toString(StandardCharsets.UTF_8));
        }
    }

    private record CommandResult(int exitCode, String standardOutput, String errorOutput) {
    }

    @FunctionalInterface
    private interface CommandInvocation {
        int invoke(PrintStream output, PrintStream errorOutput);
    }
}
