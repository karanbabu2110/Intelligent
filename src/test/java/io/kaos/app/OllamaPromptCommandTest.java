package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationHistory;
import io.kaos.memory.AnswerDetail;
import io.kaos.memory.MemoryStorageException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OllamaPromptCommandTest {
    @Test
    void appliesTheRetrievedPreferenceOnlyToOneShotPromptExecution() {
        AtomicReference<OllamaPrompt> submitted = new AtomicReference<>();
        OllamaPromptCommand command = command(
                Optional.of(AnswerDetail.DETAILED), submitted, new ByteArrayOutputStream());

        int exitCode = command.execute("Explain the decision.");

        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertEquals("Explain the decision.", submitted.get().text());
        assertEquals("Answer in detail with relevant context and explanation.",
                submitted.get().systemInstruction());
    }

    @Test
    void preservesTheExistingOneShotRequestWhenMemoryIsAbsent() {
        AtomicReference<OllamaPrompt> submitted = new AtomicReference<>();
        OllamaPromptCommand command = command(
                Optional.empty(), submitted, new ByteArrayOutputStream());

        assertEquals(KaosApplication.SUCCESS, command.execute("Answer normally."));

        assertEquals("Answer normally.", submitted.get().text());
        assertEquals("", submitted.get().systemInstruction());
    }

    @Test
    void internalConversationSubmissionDoesNotReadOrApplyMemory() {
        AtomicReference<OllamaPrompt> submitted = new AtomicReference<>();
        OllamaPromptCommand command = command(
                () -> {
                    throw new AssertionError("conversation must not retrieve memory");
                }, submitted, new ByteArrayOutputStream());

        OllamaPromptCommand.PromptOutcome outcome = command.submit(
                "Conversation prompt", ConversationHistory.empty());

        assertEquals(KaosApplication.SUCCESS, outcome.exitCode());
        assertEquals("", submitted.get().systemInstruction());
    }

    @Test
    void retrievalFailureStopsBeforeModelOrProviderSubmission() {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        CommandContext context = context(errorOutput);
        OllamaPromptCommand command = new OllamaPromptCommand(
                context,
                () -> {
                    throw new AssertionError("model must not load");
                },
                (model, history, prompt, thinking, chunks) -> {
                    throw new AssertionError("provider must not run");
                },
                () -> {
                    throw MemoryStorageException.unavailable();
                });

        int exitCode = command.execute("private prompt");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals(
                "ERROR [KAOS-MEMORY-002] The answer-detail memory could not be read. "
                        + "Check the configured memory data directory and retry."
                        + System.lineSeparator(),
                errorOutput.toString(StandardCharsets.UTF_8));
        assertFalse(errorOutput.toString(StandardCharsets.UTF_8).contains("private prompt"));
    }

    private static OllamaPromptCommand command(
            Optional<AnswerDetail> answerDetail,
            AtomicReference<OllamaPrompt> submitted,
            ByteArrayOutputStream errorOutput) {
        return command(() -> answerDetail, submitted, errorOutput);
    }

    private static OllamaPromptCommand command(
            java.util.function.Supplier<Optional<AnswerDetail>> answerDetailLoader,
            AtomicReference<OllamaPrompt> submitted,
            ByteArrayOutputStream errorOutput) {
        return new OllamaPromptCommand(
                context(errorOutput),
                () -> new OllamaModelConfiguration("qwen3"),
                (model, history, prompt, thinking, chunks) -> {
                    submitted.set(prompt);
                    chunks.accept("answer");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", "answer");
                },
                answerDetailLoader);
    }

    private static CommandContext context(ByteArrayOutputStream errorOutput) {
        return new CommandContext(
                new ApplicationConfiguration("KAOS"), InputStream.nullInputStream(),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
    }
}
