package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.kaos.app.config.ApplicationConfiguration;
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

        int exitCode = new KnowledgeIngestCommand(output.context()).execute(document.toString());

        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertEquals(
                "Ingested document: knowledge.txt (type: text/plain; charset=utf-8, bytes: 13, "
                        + "characters: 13, chunks: 1)." + System.lineSeparator(),
                output.standardOutput());
        assertEquals("", output.errorOutput());
    }

    @Test
    void reportsAContentFreeFailureForAnUnavailableDocument() {
        Path document = temporaryDirectory.resolve("private-missing.txt");
        Output output = new Output();

        int exitCode = new KnowledgeIngestCommand(output.context()).execute(document.toString());

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.standardOutput());
        assertEquals(
                "ERROR [KAOS-KNOWLEDGE-002] The local text document could not be read. "
                        + "Check that it exists and is accessible, then retry."
                        + System.lineSeparator(),
                output.errorOutput());
        assertFalse(output.errorOutput().contains(document.toString()));
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
