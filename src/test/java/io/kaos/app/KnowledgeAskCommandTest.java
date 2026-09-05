package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.EmbeddedChunk;
import io.kaos.knowledge.StoredKnowledgeDocument;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class KnowledgeAskCommandTest {
    @Test
    void answersThroughTheApplicationRuntimeBoundary() {
        AtomicReference<String> submittedPrompt = new AtomicReference<>();
        KaosApplicationHarness.Result result = KaosApplicationHarness.capture(
                (standard, error) -> new ApplicationRuntime(
                        () -> new OllamaConnectivity.Result(
                                OllamaConnectivity.Status.REACHABLE, "test"),
                        () -> new OllamaModelConfiguration("qwen3"),
                        (model, history, prompt, thinking, chunks) -> {
                            submittedPrompt.set(prompt.text());
                            chunks.accept("Nightly [1].");
                            return new OllamaPromptClient.Result(
                                    OllamaPromptClient.Status.SUCCESS, "", "Nightly [1].");
                        },
                        () -> Path.of("unused-conversations.db"),
                        () -> new OllamaEmbeddingConfiguration("embeddinggemma"),
                        (configuration, chunks) -> new OllamaEmbeddingClient.Result(
                                OllamaEmbeddingClient.Status.SUCCESS,
                                List.of(new EmbeddedChunk(
                                        chunks.getFirst(), new double[] {1, 0}))),
                        (model, chunks) -> {
                            throw new AssertionError("knowledge storage must not run");
                        },
                        () -> List.of(document("Backups run nightly.")))
                        .execute(
                                new String[] {"knowledge-ask", "When do backups run?"},
                                new ApplicationConfiguration("KAOS"),
                                InputStream.nullInputStream(), standard, error));

        assertEquals(KaosApplication.SUCCESS, result.exitCode());
        assertEquals("Nightly [1]." + System.lineSeparator()
                + "Citation sources: 1." + System.lineSeparator()
                + "citation [1]: document: 7, source: notes.txt, chunk: 0"
                + System.lineSeparator(), result.standardOutput());
        assertEquals("", result.errorOutput());
        assertTrue(submittedPrompt.get().contains("CONTENT:\nBackups run nightly."));
    }

    @Test
    void submitsTheExactGroundedPromptAndPrintsCitationsAfterTheAnswer() {
        Output output = new Output();
        AtomicReference<String> submittedPrompt = new AtomicReference<>();
        KnowledgeAskCommand command = command(output,
                List.of(document("Backups run nightly.")),
                (model, history, prompt, thinking, chunks) -> {
                    submittedPrompt.set(prompt.text());
                    assertTrue(history.messages().isEmpty());
                    chunks.accept("Backups run nightly [1].");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.SUCCESS, "", "Backups run nightly [1].");
                });

        int exitCode = command.execute("When do backups run?");

        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertTrue(submittedPrompt.get().contains("QUESTION:\nWhen do backups run?"));
        assertTrue(submittedPrompt.get().contains("CONTENT:\nBackups run nightly."));
        assertTrue(submittedPrompt.get().contains("CITATION: [1]"));
        assertEquals("Backups run nightly [1]." + System.lineSeparator()
                + "Citation sources: 1." + System.lineSeparator()
                + "citation [1]: document: 7, source: notes.txt, chunk: 0"
                + System.lineSeparator(), output.standardOutput());
        assertEquals("", output.errorOutput());
    }

    @Test
    void withholdsCitationsAfterPartialProviderFailure() {
        Output output = new Output();
        KnowledgeAskCommand command = command(output,
                List.of(document("private stored fact")),
                (model, history, prompt, thinking, chunks) -> {
                    chunks.accept("partial answer");
                    return new OllamaPromptClient.Result(
                            OllamaPromptClient.Status.INVALID_RESPONSE, "", "");
                });

        int exitCode = command.execute("private question");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("partial answer" + System.lineSeparator(), output.standardOutput());
        assertTrue(output.errorOutput().contains("ERROR [KAOS-AI-002]"));
        assertFalse(output.standardOutput().contains("Citation sources"));
        assertFalse(output.errorOutput().contains("private"));
    }

    @Test
    void doesNotSubmitWhenCompatibleContextIsUnavailable() {
        Output output = new Output();
        KnowledgeAskCommand command = command(output, List.of(),
                (model, history, prompt, thinking, chunks) -> {
                    throw new AssertionError("prompt submission must not run");
                });

        int exitCode = command.execute("private question");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.standardOutput());
        assertTrue(output.errorOutput().contains("ERROR [KAOS-KNOWLEDGE-009]"));
        assertFalse(output.errorOutput().contains("private"));
    }

    private static KnowledgeAskCommand command(
            Output output,
            List<StoredKnowledgeDocument> documents,
            OllamaPromptSubmission promptSubmission) {
        KnowledgeRetrieveCommand retrieval = new KnowledgeRetrieveCommand(
                output.context(),
                () -> new OllamaEmbeddingConfiguration("embeddinggemma"),
                (configuration, chunks) -> new OllamaEmbeddingClient.Result(
                        OllamaEmbeddingClient.Status.SUCCESS,
                        List.of(new EmbeddedChunk(chunks.getFirst(), new double[] {1, 0}))),
                () -> documents);
        OllamaPromptCommand prompt = new OllamaPromptCommand(
                output.context(), () -> new OllamaModelConfiguration("qwen3"),
                promptSubmission);
        return new KnowledgeAskCommand(retrieval, prompt);
    }

    private static StoredKnowledgeDocument document(String content) {
        DocumentChunk chunk = new DocumentChunk(
                "notes.txt", 0, 0, content.codePointCount(0, content.length()), content);
        return new StoredKnowledgeDocument(7, "embeddinggemma",
                List.of(new EmbeddedChunk(chunk, new double[] {1, 0})));
    }

    private static final class Output {
        private final ByteArrayOutputStream standard = new ByteArrayOutputStream();
        private final ByteArrayOutputStream error = new ByteArrayOutputStream();

        private CommandContext context() {
            return new CommandContext(new ApplicationConfiguration("KAOS"),
                    InputStream.nullInputStream(),
                    new PrintStream(standard, true, StandardCharsets.UTF_8),
                    new PrintStream(error, true, StandardCharsets.UTF_8));
        }

        private String standardOutput() { return standard.toString(StandardCharsets.UTF_8); }
        private String errorOutput() { return error.toString(StandardCharsets.UTF_8); }
    }
}
