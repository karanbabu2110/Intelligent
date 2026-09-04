package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.EmbeddedChunk;
import io.kaos.knowledge.StoredKnowledgeDocument;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeRetrieveCommandTest {
    @Test
    void embedsTheExactQueryAndPrintsOnlyRankedReferences() {
        Output output = new Output();
        String privateQuery = "private semantic query";
        KnowledgeRetrieveCommand command = new KnowledgeRetrieveCommand(
                output.context(),
                () -> new OllamaEmbeddingConfiguration("embeddinggemma"),
                (configuration, chunks) -> {
                    assertEquals(privateQuery, chunks.getFirst().content());
                    return success(chunks.getFirst(), 1, 0);
                },
                () -> List.of(new StoredKnowledgeDocument(7, "embeddinggemma", List.of(
                        embedded("private stored content", 0, 1, 0),
                        embedded("other private content", 1, 0, 1)))));

        int exitCode = command.execute(privateQuery);

        assertEquals(KaosApplication.SUCCESS, exitCode);
        String referenceOutput = "Retrieved context: 2 matches." + System.lineSeparator()
                + "document: 7, source: notes.txt, chunk: 0, score: 1.000000"
                + System.lineSeparator()
                + "document: 7, source: notes.txt, chunk: 1, score: 0.000000"
                + System.lineSeparator();
        assertTrue(output.standardOutput().startsWith(referenceOutput));
        assertTrue(output.standardOutput().substring(referenceOutput.length())
                .matches("Grounded prompt: [1-9][0-9]* characters from 2 contexts\\.\\R"));
        assertFalse(output.standardOutput().contains("private"));
        assertEquals("", output.errorOutput());
    }

    @Test
    void reportsNoCompatibleContextWithoutEchoingTheQuery() {
        Output output = new Output();
        KnowledgeRetrieveCommand command = new KnowledgeRetrieveCommand(
                output.context(),
                () -> new OllamaEmbeddingConfiguration("new-model"),
                (configuration, chunks) -> success(chunks.getFirst(), 1, 0),
                () -> List.of(new StoredKnowledgeDocument(1, "old-model",
                        List.of(embedded("content", 0, 1, 0)))));

        int exitCode = command.execute("private query");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.standardOutput());
        assertEquals("ERROR [KAOS-KNOWLEDGE-009] No compatible stored context is available. "
                + "Ingest a document with the configured embedding model and retry."
                + System.lineSeparator(), output.errorOutput());
        assertFalse(output.errorOutput().contains("private"));
    }

    @Test
    void classifiesAZeroQueryVectorAsAnUnusableProviderResponse() {
        Output output = new Output();
        KnowledgeRetrieveCommand command = new KnowledgeRetrieveCommand(
                output.context(),
                () -> new OllamaEmbeddingConfiguration("embeddinggemma"),
                (configuration, chunks) -> success(chunks.getFirst(), 0, 0),
                List::of);

        int exitCode = command.execute("private query");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("ERROR [KAOS-KNOWLEDGE-006] Ollama returned an unusable query embedding. "
                + "Check the configured embedding model and retry."
                + System.lineSeparator(), output.errorOutput());
        assertFalse(output.errorOutput().contains("private"));
    }

    @Test
    void rejectsUnsafeStoredContentWithoutPrintingIt() {
        Output output = new Output();
        KnowledgeRetrieveCommand command = new KnowledgeRetrieveCommand(
                output.context(),
                () -> new OllamaEmbeddingConfiguration("embeddinggemma"),
                (configuration, chunks) -> success(chunks.getFirst(), 1, 0),
                () -> List.of(new StoredKnowledgeDocument(1, "embeddinggemma",
                        List.of(embedded("private\u0000content", 0, 1, 0)))));

        int exitCode = command.execute("question");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.standardOutput());
        assertEquals("ERROR [KAOS-KNOWLEDGE-010] The retrieved context could not form a "
                + "safe bounded prompt. Check stored document content and retry."
                + System.lineSeparator(), output.errorOutput());
        assertFalse(output.errorOutput().contains("private"));
    }

    private static OllamaEmbeddingClient.Result success(DocumentChunk chunk, double... vector) {
        return new OllamaEmbeddingClient.Result(OllamaEmbeddingClient.Status.SUCCESS,
                List.of(new EmbeddedChunk(chunk, vector)));
    }

    private static EmbeddedChunk embedded(String content, int index, double... vector) {
        return new EmbeddedChunk(new DocumentChunk("notes.txt", index,
                index * content.length(), (index + 1) * content.length(), content), vector);
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
