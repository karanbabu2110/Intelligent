package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
    void reportsAReachableLocalOllamaVersion() {
        KaosApplicationHarness.Result result = runOllamaStatus(
                new OllamaConnectivity.Result(
                        OllamaConnectivity.Status.REACHABLE, "0.11.4"));

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Local Ollama is reachable (version 0.11.4)." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void reportsUnavailableOllamaWithSafeRecoveryGuidance() {
        KaosApplicationHarness.Result result = runOllamaStatus(
                new OllamaConnectivity.Result(
                        OllamaConnectivity.Status.UNAVAILABLE, ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-001] Local Ollama is unavailable. "
                        + "Start Ollama on 127.0.0.1:11434 and retry."
                        + System.lineSeparator(),
                result.errorOutput());
    }

    @Test
    void reportsAnInvalidOllamaResponseWithoutProviderDetails() {
        KaosApplicationHarness.Result result = runOllamaStatus(
                new OllamaConnectivity.Result(
                        OllamaConnectivity.Status.INVALID_RESPONSE, ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-001] Local Ollama returned an invalid version response. "
                        + "Verify Ollama and retry."
                        + System.lineSeparator(),
                result.errorOutput());
    }

    @Test
    void reportsAnInterruptedOllamaCheckWithSafeRecoveryGuidance() {
        KaosApplicationHarness.Result result = runOllamaStatus(
                new OllamaConnectivity.Result(
                        OllamaConnectivity.Status.INTERRUPTED, ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-001] The Ollama connectivity check was interrupted. "
                        + "Retry the command."
                        + System.lineSeparator(),
                result.errorOutput());
    }

    @Test
    void reportsTheExplicitlyConfiguredOllamaModel() {
        KaosApplicationHarness.Result result = runOllamaModel(
                () -> new OllamaModelConfiguration("llama3.2:latest"));

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Configured local Ollama model: llama3.2:latest "
                        + "(context window: 4096 tokens, thinking: off, "
                        + "response limit: 512 tokens)."
                        + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
    }

    @Test
    void reportsInvalidOllamaModelConfigurationWithoutDisclosingItsValue() {
        String privateDetail = "private-invalid-model";

        KaosApplicationHarness.Result result = runOllamaModel(() -> {
            throw new IllegalArgumentException(privateDetail);
        });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-CONFIG-001] Invalid Ollama configuration. "
                        + "Check model, context-window, thinking, and response-token-limit "
                        + "process settings and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateDetail));
    }

    @Test
    void reportsUnreadableOllamaModelConfigurationWithoutExceptionDetails() {
        String privateDetail = "private-permission-detail";

        KaosApplicationHarness.Result result = runOllamaModel(() -> {
            throw new IllegalStateException(privateDetail);
        });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-CONFIG-002] Ollama model configuration could not be read. "
                        + "Check process permissions and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privateDetail));
    }

    @Test
    void statusDoesNotLoadOllamaModelConfiguration() {
        KaosApplicationHarness.Result result = runWithModelLoader(
                new String[] {"status"},
                () -> {
                    throw new AssertionError("model configuration must remain lazy");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                result.standardOutput());
    }

    @Test
    void submitsOnePromptToTheConfiguredModelAndPrintsTheCompleteResponse() {
        AtomicReference<String> selectedModel = new AtomicReference<>();
        AtomicReference<String> submittedPrompt = new AtomicReference<>();

        KaosApplicationHarness.Result result = runOllamaPrompt(
                "Why local AI?",
                () -> new OllamaModelConfiguration("qwen3:8b"),
                (model, prompt) -> {
                    selectedModel.set(model.modelName());
                    submittedPrompt.set(prompt.text());
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS,
                            "private reasoning trace",
                            "A local answer.");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals("A local answer." + System.lineSeparator(), result.standardOutput());
        assertEquals("", result.errorOutput());
        assertFalse(result.standardOutput().contains("private reasoning trace"));
        assertEquals("qwen3:8b", selectedModel.get());
        assertEquals("Why local AI?", submittedPrompt.get());
    }

    @Test
    void rejectsAMissingPromptWithUsageGuidance() {
        KaosApplicationHarness.Result result = KaosApplicationHarness.run("ollama-prompt");

        assertEquals(KaosApplication.USAGE_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "Expected one quoted prompt. Run 'kaos help' for usage."
                        + System.lineSeparator(),
                result.errorOutput());
    }

    @Test
    void reportsTokenLimitCompletionWithoutPrintingAPartialAnswer() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, prompt) -> new OllamaPromptClient.Result(
                        OllamaPromptClient.Status.TOKEN_LIMIT_REACHED, "", ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-003] Ollama reached a response or context length boundary "
                        + "before completing the answer. Review the response-token limit and "
                        + "context window, then retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void rejectsAnInvalidPromptWithoutEchoingItOrLoadingTheModel() {
        String privatePrompt = "private\u001b[31m-prompt";

        KaosApplicationHarness.Result result = runOllamaPrompt(
                privatePrompt,
                () -> {
                    throw new AssertionError("model must not load for an invalid prompt");
                },
                (model, prompt) -> {
                    throw new AssertionError("invalid prompt must not be submitted");
                });

        assertEquals(KaosApplication.USAGE_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "Expected one valid quoted prompt. Run 'kaos help' for usage."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privatePrompt));
    }

    @Test
    void reportsPromptRejectionWithoutEchoingPromptOrProviderDetails() {
        String privatePrompt = "private prompt";
        String privateProviderDetail = "private provider detail";

        KaosApplicationHarness.Result result = runOllamaPrompt(
                privatePrompt,
                () -> new OllamaModelConfiguration("missing-model"),
                (model, prompt) -> {
                    assertFalse(prompt.text().contains(privateProviderDetail));
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.REQUEST_FAILED, "", "");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-002] Local Ollama rejected the prompt request. "
                        + "Verify the configured model and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains(privatePrompt));
        assertFalse(result.errorOutput().contains(privateProviderDetail));
    }

    @Test
    void reportsPromptTimeoutWithActionableGuidance() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("slow-model"),
                (model, prompt) -> new OllamaPromptClient.Result(
                        OllamaPromptClient.Status.TIMED_OUT, "", ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-002] The Ollama prompt request timed out. "
                        + "Try again or select a faster local model."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void reportsMissingModelConfigurationBeforeSubmittingThePrompt() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> {
                    throw new IllegalArgumentException("private-model-detail");
                },
                (model, prompt) -> {
                    throw new AssertionError("prompt must not submit without a model");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-CONFIG-001] Invalid Ollama configuration. "
                        + "Check model, context-window, thinking, and response-token-limit "
                        + "process settings and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
        assertFalse(result.errorOutput().contains("private-model-detail"));
    }

    @Test
    void statusDoesNotCreateThePromptClient() {
        KaosApplicationHarness.Result result = runWithPromptSubmission(
                new String[] {"status"},
                () -> {
                    throw new AssertionError("model configuration must remain lazy");
                },
                (model, prompt) -> {
                    throw new AssertionError("prompt client must remain lazy");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
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

    private static KaosApplicationHarness.Result runOllamaStatus(
            OllamaConnectivity.Result ollamaResult) {
        return KaosApplicationHarness.capture(
                (output, errorOutput) -> KaosApplication.run(
                        new String[] {"ollama-status"},
                        new ApplicationConfiguration(
                                ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                        () -> ollamaResult,
                        output,
                        errorOutput));
    }

    private static KaosApplicationHarness.Result runOllamaModel(
            Supplier<OllamaModelConfiguration> modelLoader) {
        return runWithModelLoader(new String[] {"ollama-model"}, modelLoader);
    }

    private static KaosApplicationHarness.Result runWithModelLoader(
            String[] arguments,
            Supplier<OllamaModelConfiguration> modelLoader) {
        return KaosApplicationHarness.capture(
                (output, errorOutput) -> KaosApplication.run(
                        arguments,
                        new ApplicationConfiguration(
                                ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                        () -> new OllamaConnectivity.Result(
                                OllamaConnectivity.Status.REACHABLE, "test-version"),
                        modelLoader,
                        output,
                        errorOutput));
    }

    private static KaosApplicationHarness.Result runOllamaPrompt(
            String prompt,
            Supplier<OllamaModelConfiguration> modelLoader,
            BiFunction<OllamaModelConfiguration, OllamaPrompt, OllamaPromptClient.Result>
                    submission) {
        return runWithPromptSubmission(
                new String[] {"ollama-prompt", prompt}, modelLoader, submission);
    }

    private static KaosApplicationHarness.Result runWithPromptSubmission(
            String[] arguments,
            Supplier<OllamaModelConfiguration> modelLoader,
            BiFunction<OllamaModelConfiguration, OllamaPrompt, OllamaPromptClient.Result>
                    submission) {
        return KaosApplicationHarness.capture(
                (output, errorOutput) -> KaosApplication.run(
                        arguments,
                        new ApplicationConfiguration(
                                ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                        () -> new OllamaConnectivity.Result(
                                OllamaConnectivity.Status.REACHABLE, "test-version"),
                        modelLoader,
                        submission,
                        output,
                        errorOutput));
    }
}
