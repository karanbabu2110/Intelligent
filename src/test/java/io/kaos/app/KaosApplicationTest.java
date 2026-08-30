package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaThinkingMode;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationHistory;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        AtomicReference<ConversationHistory> submittedHistory = new AtomicReference<>();

        KaosApplicationHarness.Result result = runOllamaPrompt(
                "Why local AI?",
                () -> new OllamaModelConfiguration("qwen3:8b"),
                (model, history, prompt, thinking, chunks) -> {
                    selectedModel.set(model.modelName());
                    submittedPrompt.set(prompt.text());
                    submittedHistory.set(history);
                    chunks.accept("A local ");
                    chunks.accept("answer.");
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
        assertTrue(submittedHistory.get().messages().isEmpty());
    }

    @Test
    void sendsEarlierCleanTurnsWithTheNextConversationPrompt() {
        List<ConversationHistory> submittedHistories = new ArrayList<>();
        List<String> submittedPrompts = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        KaosApplicationHarness.Result result = runConversation(
                "My name is Karan.\nWhat is my name?\n/exit\n",
                (model, history, prompt, thinking, chunks) -> {
                    submittedHistories.add(history);
                    submittedPrompts.add(prompt.text());
                    String answer = calls.getAndIncrement() == 0 ? "Understood." : "Karan.";
                    chunks.accept(answer);
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", answer);
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(List.of("My name is Karan.", "What is my name?"), submittedPrompts);
        assertTrue(submittedHistories.get(0).messages().isEmpty());
        assertEquals(List.of("My name is Karan.", "Understood."),
                submittedHistories.get(1).messages().stream()
                        .map(message -> message.content())
                        .toList());
        assertTrue(result.standardOutput().contains("Understood."));
        assertTrue(result.standardOutput().contains("Karan."));
        assertEquals("", result.errorOutput());
    }

    @Test
    void createsSelectsAndIsolatesConversationHistories() {
        List<ConversationHistory> submittedHistories = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        KaosApplicationHarness.Result result = runConversation(
                "First topic\n/new\nSecond topic\n/select 1\nFollow up first\n/list\n/exit\n",
                (model, history, prompt, thinking, chunks) -> {
                    submittedHistories.add(history);
                    String answer = "Answer " + calls.incrementAndGet();
                    chunks.accept(answer);
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", answer);
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(3, submittedHistories.size());
        assertTrue(submittedHistories.get(0).messages().isEmpty());
        assertTrue(submittedHistories.get(1).messages().isEmpty());
        assertEquals(List.of("First topic", "Answer 1"),
                submittedHistories.get(2).messages().stream()
                        .map(message -> message.content())
                        .toList());
        assertTrue(result.standardOutput().contains(
                "Conversation 2 created and selected."));
        assertTrue(result.standardOutput().contains("Conversation 1 selected."));
        assertTrue(result.standardOutput().contains("Conversations: *1 2"));
        assertEquals("", result.errorOutput());
    }

    @Test
    void invalidSelectionsDoNotChangeTheActiveConversation() {
        AtomicReference<ConversationHistory> submittedHistory = new AtomicReference<>();

        KaosApplicationHarness.Result result = runConversation(
                "/new\n/select nope\n/select 99\nQuestion\n/exit\n",
                (model, history, prompt, thinking, chunks) -> {
                    submittedHistory.set(history);
                    chunks.accept("Answer");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", "Answer");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertTrue(submittedHistory.get().messages().isEmpty());
        assertTrue(result.standardOutput().contains("kaos[2]>"));
        assertTrue(result.errorOutput().contains("Expected /select <existing-id>."));
        assertTrue(result.errorOutput().contains("Conversation does not exist."));
    }

    @Test
    void failedTurnsAreNotRetainedBeforeTheNextPrompt() {
        List<ConversationHistory> submittedHistories = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        KaosApplicationHarness.Result result = runConversation(
                "Failed prompt\nClean prompt\n/exit\n",
                (model, history, prompt, thinking, chunks) -> {
                    submittedHistories.add(history);
                    if (calls.getAndIncrement() == 0) {
                        return new OllamaPromptClient.Result(
                                OllamaPromptClient.Status.INVALID_RESPONSE, "", "");
                    }
                    chunks.accept("Clean answer");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", "Clean answer");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals(2, submittedHistories.size());
        assertTrue(submittedHistories.get(0).messages().isEmpty());
        assertTrue(submittedHistories.get(1).messages().isEmpty());
        assertTrue(result.standardOutput().contains("Clean answer"));
        assertTrue(result.errorOutput().contains("ERROR [KAOS-AI-002]"));
        assertFalse(result.errorOutput().contains("Failed prompt"));
    }

    @Test
    void endsAnEmptyConversationSessionAtEndOfInput() {
        KaosApplicationHarness.Result result = runConversation(
                "",
                (model, history, prompt, thinking, chunks) -> {
                    throw new AssertionError("end-of-input must not submit a prompt");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertTrue(result.standardOutput().contains("Conversation 1 created and selected."));
        assertTrue(result.standardOutput().endsWith(
                "Conversation session ended." + System.lineSeparator()));
        assertEquals("", result.errorOutput());
    }

    @Test
    void displaysConversationControlsWithoutSubmittingAPrompt() {
        KaosApplicationHarness.Result result = runConversation(
                "/help\n/exit\n",
                (model, history, prompt, thinking, chunks) -> {
                    throw new AssertionError("help must not submit a prompt");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertTrue(result.standardOutput().contains("Conversation controls:"));
        assertTrue(result.standardOutput().contains("/select <id>"));
        assertTrue(result.standardOutput().contains("/exit"));
        assertEquals("", result.errorOutput());
    }

    @Test
    void showsThinkingProgressAndTransitionsToTheAnswerWithoutRawReasoning() {
        String privateThinking = "private reasoning trace";
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "Solve this deliberately.",
                () -> new OllamaModelConfiguration(
                        "qwen3:4b", 4_096, OllamaThinkingMode.ON),
                (model, history, prompt, thinking, chunks) -> {
                    thinking.run();
                    thinking.run();
                    chunks.accept("Final ");
                    chunks.accept("answer.");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS,
                            privateThinking,
                            "Final answer.");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Thinking..." + System.lineSeparator()
                        + "Answer:" + System.lineSeparator()
                        + "Final answer." + System.lineSeparator(),
                result.standardOutput());
        assertEquals("", result.errorOutput());
        assertFalse(result.standardOutput().contains(privateThinking));
    }

    @Test
    void thinkingOnDoesNotClaimProgressWhenTheProviderEmitsNoThinking() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "Answer directly.",
                () -> new OllamaModelConfiguration(
                        "qwen3:4b", 4_096, OllamaThinkingMode.ON),
                (model, history, prompt, thinking, chunks) -> {
                    chunks.accept("Direct answer.");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", "Direct answer.");
                });

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals(
                "Answer:" + System.lineSeparator()
                        + "Direct answer." + System.lineSeparator(),
                result.standardOutput());
        assertFalse(result.standardOutput().contains("Thinking..."));
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
    void labelsVisibleOutputWhenTheProviderReachesItsTokenLimit() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, history, prompt, thinking, chunks) -> {
                    chunks.accept("partial answer");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.TOKEN_LIMIT_REACHED, "", "");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("partial answer" + System.lineSeparator(), result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-003] Partial streaming output was displayed before clean "
                        + "completion. Ollama reached a response or context length boundary "
                        + "before completing the answer. Review the response-token limit and "
                        + "context window, then retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void labelsVisibleOutputWhenAStreamIsMalformedAfterAnAnswerChunk() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, history, prompt, thinking, chunks) -> {
                    chunks.accept("safe prefix");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.INVALID_RESPONSE, "", "");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("safe prefix" + System.lineSeparator(), result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-002] Partial streaming output was displayed before clean "
                        + "completion. Local Ollama returned an invalid prompt response. "
                        + "Verify Ollama and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void labelsVisibleOutputWhenTheStreamIsCancelled() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, history, prompt, thinking, chunks) -> {
                    chunks.accept("safe prefix");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.INTERRUPTED, "", "");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("safe prefix" + System.lineSeparator(), result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-006] Partial streaming output was displayed before clean "
                        + "completion. The Ollama prompt request was cancelled. Retry when ready."
                        + System.lineSeparator(),
                result.errorOutput());
    }

    @Test
    void distinguishesALocalStreamSafetyLimitFromProviderTruncation() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, history, prompt, thinking, chunks) -> new OllamaPromptClient.Result(
                        OllamaPromptClient.Status.LOCAL_LIMIT_REACHED, "", ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-003] KAOS stopped the Ollama stream at a local byte or text "
                        + "safety limit. Shorten the request or response, then retry."
                        + System.lineSeparator(),
                result.errorOutput());
    }

    @Test
    void rejectsAnInvalidPromptWithoutEchoingItOrLoadingTheModel() {
        String privatePrompt = "private\u001b[31m-prompt";

        KaosApplicationHarness.Result result = runOllamaPrompt(
                privatePrompt,
                () -> {
                    throw new AssertionError("model must not load for an invalid prompt");
                },
                (model, history, prompt, thinking, chunks) -> {
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
                (model, history, prompt, thinking, chunks) -> {
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
    void reportsUnavailableOllamaBeforeThePromptResponseBegins() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, history, prompt, thinking, chunks) -> new OllamaPromptClient.Result(
                        OllamaPromptClient.Status.UNAVAILABLE, "", ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-001] Local Ollama could not be reached before the prompt "
                        + "response began. Start Ollama on 127.0.0.1:11434 and retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void reportsAcceptedStreamTransportFailureSeparatelyFromUnavailability() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("qwen3:4b-instruct"),
                (model, history, prompt, thinking, chunks) -> {
                    chunks.accept("safe prefix");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.STREAM_FAILED, "", "");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("safe prefix" + System.lineSeparator(), result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-004] Partial streaming output was displayed before clean "
                        + "completion. The accepted Ollama response stream lost its local "
                        + "connection. Verify Ollama is still running, then retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void reportsTotalPromptTimeoutWithActionableGuidance() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("slow-model"),
                (model, history, prompt, thinking, chunks) -> new OllamaPromptClient.Result(
                        OllamaPromptClient.Status.TOTAL_TIMEOUT, "", ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-005] The Ollama prompt exceeded its five-minute total "
                        + "deadline. Shorten the request or select a faster local model, "
                        + "then retry."
                        + System.lineSeparator(),
                result.errorOutput());
        assertFalse(result.errorOutput().contains("private prompt"));
    }

    @Test
    void reportsStreamInactivityTimeoutWithDifferentRecoveryGuidance() {
        KaosApplicationHarness.Result result = runOllamaPrompt(
                "private prompt",
                () -> new OllamaModelConfiguration("stalled-model"),
                (model, history, prompt, thinking, chunks) -> new OllamaPromptClient.Result(
                        OllamaPromptClient.Status.INACTIVITY_TIMEOUT, "", ""));

        assertEquals(KaosApplication.APPLICATION_ERROR, result.exitCode());
        assertEquals("", result.standardOutput());
        assertEquals(
                "ERROR [KAOS-AI-005] The Ollama response stream produced no data for 60 "
                        + "seconds. Verify Ollama is still progressing or select a faster "
                        + "local model, then retry."
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
                (model, history, prompt, thinking, chunks) -> {
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
                (model, history, prompt, thinking, chunks) -> {
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
            KaosApplication.OllamaPromptSubmission submission) {
        return runWithPromptSubmission(
                new String[] {"ollama-prompt", prompt}, modelLoader, submission);
    }

    private static KaosApplicationHarness.Result runConversation(
            String input,
            KaosApplication.OllamaPromptSubmission submission) {
        return KaosApplicationHarness.captureInput(input,
                (testInput, output, errorOutput) -> KaosApplication.run(
                        new String[] {"conversation"},
                        new ApplicationConfiguration(
                                ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                        () -> new OllamaConnectivity.Result(
                                OllamaConnectivity.Status.REACHABLE, "test-version"),
                        () -> new OllamaModelConfiguration("qwen3"),
                        submission,
                        testInput,
                        output,
                        errorOutput));
    }

    private static KaosApplicationHarness.Result runWithPromptSubmission(
            String[] arguments,
            Supplier<OllamaModelConfiguration> modelLoader,
            KaosApplication.OllamaPromptSubmission submission) {
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
