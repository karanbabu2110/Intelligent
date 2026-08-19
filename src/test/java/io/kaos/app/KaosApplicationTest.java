package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

    private static CommandResult run(String... arguments) {
        return run(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                arguments);
    }

    private static CommandResult run(
            ApplicationConfiguration configuration, String... arguments) {
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

        try (PrintStream testOutput =
                        new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
                PrintStream testError =
                        new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            int exitCode = KaosApplication.run(arguments, configuration, testOutput, testError);
            return new CommandResult(
                    exitCode,
                    capturedOutput.toString(StandardCharsets.UTF_8),
                    capturedError.toString(StandardCharsets.UTF_8));
        }
    }

    private record CommandResult(int exitCode, String standardOutput, String errorOutput) {
    }
}
