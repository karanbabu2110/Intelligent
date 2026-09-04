package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.knowledge.EmbeddedChunk;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeIngestCommandTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void coordinatesTheKnowledgeIngestionPipeline() throws Exception {
        Path document = temporaryDirectory.resolve("knowledge.txt");
        Files.writeString(document, "grounded text", StandardCharsets.UTF_8);
        Output output = new Output();

        int exitCode = successfulCommand(output).execute(document.toString());

        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertEquals(
                "Ingested document: knowledge.txt (type: text/plain; charset=utf-8, bytes: 13, "
                        + "characters: 13, chunks: 1, embeddings: 1, dimensions: 3)."
                        + System.lineSeparator(),
                output.standardOutput());
        assertEquals("", output.errorOutput());
    }

    @Test
    void reportsAContentFreeFailureForAnUnavailableDocument() {
        Path document = temporaryDirectory.resolve("private-missing.txt");
        Output output = new Output();

        int exitCode = successfulCommand(output).execute(document.toString());

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.standardOutput());
        assertEquals(
                "ERROR [KAOS-KNOWLEDGE-002] The local text document could not be read. "
                        + "Check that it exists and is accessible, then retry."
                        + System.lineSeparator(),
                output.errorOutput());
        assertFalse(output.errorOutput().contains(document.toString()));
    }

    @Test
    void requiresAnExplicitEmbeddingModelWithoutExposingConfiguration() throws Exception {
        Path document = temporaryDirectory.resolve("knowledge.txt");
        Files.writeString(document, "private grounded text", StandardCharsets.UTF_8);
        Output output = new Output();
        KnowledgeIngestCommand command = new KnowledgeIngestCommand(
                output.context(),
                () -> { throw new IllegalArgumentException("private configuration"); },
                (configuration, chunks) -> { throw new AssertionError("must not submit"); });

        int exitCode = command.execute(document.toString());

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.standardOutput());
        assertEquals("ERROR [KAOS-KNOWLEDGE-CONFIG-001] Configure one installed local Ollama "
                        + "embedding model with KAOS_OLLAMA_EMBEDDING_MODEL, then retry."
                        + System.lineSeparator(), output.errorOutput());
        assertFalse(output.errorOutput().contains("private"));
    }

    private KnowledgeIngestCommand successfulCommand(Output output) {
        return new KnowledgeIngestCommand(
                output.context(),
                () -> new OllamaEmbeddingConfiguration("embeddinggemma"),
                (configuration, chunks) -> new OllamaEmbeddingClient.Result(
                        OllamaEmbeddingClient.Status.SUCCESS,
                        chunks.stream().map(chunk ->
                                new EmbeddedChunk(chunk, new double[] {0.1, 0.2, 0.3})).toList()));
    }

    private static final class Output {
        private final ByteArrayOutputStream standardBytes = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        private CommandContext context() {
            return new CommandContext(
                    new ApplicationConfiguration("KAOS"),
                    InputStream.nullInputStream(),
                    new PrintStream(standardBytes, true, StandardCharsets.UTF_8),
                    new PrintStream(errorBytes, true, StandardCharsets.UTF_8));
        }

        private String standardOutput() {
            return standardBytes.toString(StandardCharsets.UTF_8);
        }

        private String errorOutput() {
            return errorBytes.toString(StandardCharsets.UTF_8);
        }
    }
}
