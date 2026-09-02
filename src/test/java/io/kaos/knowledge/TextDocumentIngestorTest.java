package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.knowledge.KnowledgeIngestionException.Reason;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextDocumentIngestorTest {
    @TempDir
    Path temporaryDirectory;

    private final TextDocumentIngestor ingestor = new TextDocumentIngestor();

    @Test
    void ingestsExactUtf8BytesAndSafeMetadata() throws Exception {
        byte[] expected = "KAOS knows café and ಕನ್ನಡ.\n".getBytes(StandardCharsets.UTF_8);
        Path documentPath = temporaryDirectory.resolve("notes.TXT");
        Files.write(documentPath, expected);

        IngestedDocument document = ingestor.ingest(documentPath);

        assertEquals("notes.TXT", document.name());
        assertEquals(TextDocumentIngestor.MEDIA_TYPE, document.mediaType());
        assertEquals(expected.length, document.byteCount());
        assertArrayEquals(expected, document.content());
    }

    @Test
    void protectsTheAdmittedBytesFromCallerMutation() throws Exception {
        Path documentPath = temporaryDirectory.resolve("notes.txt");
        Files.writeString(documentPath, "trusted", StandardCharsets.UTF_8);
        IngestedDocument document = ingestor.ingest(documentPath);

        byte[] exposed = document.content();
        exposed[0] = 'X';

        assertArrayEquals("trusted".getBytes(StandardCharsets.UTF_8), document.content());
    }

    @Test
    void acceptsTheExactByteLimit() throws Exception {
        Path documentPath = temporaryDirectory.resolve("limit.txt");
        byte[] content = new byte[TextDocumentIngestor.MAX_DOCUMENT_BYTES];
        java.util.Arrays.fill(content, (byte) 'a');
        Files.write(documentPath, content);

        assertEquals(TextDocumentIngestor.MAX_DOCUMENT_BYTES,
                ingestor.ingest(documentPath).byteCount());
    }

    @Test
    void rejectsAnOversizedDocument() throws Exception {
        Path documentPath = temporaryDirectory.resolve("oversized.txt");
        Files.write(documentPath, new byte[TextDocumentIngestor.MAX_DOCUMENT_BYTES + 1]);

        assertFailure(documentPath, Reason.TOO_LARGE);
    }

    @Test
    void rejectsAnEmptyDocument() throws Exception {
        Path documentPath = Files.createFile(temporaryDirectory.resolve("empty.txt"));

        assertFailure(documentPath, Reason.INVALID_DOCUMENT);
    }

    @Test
    void rejectsInvalidUtf8() throws Exception {
        Path documentPath = temporaryDirectory.resolve("invalid.txt");
        Files.write(documentPath, new byte[] {(byte) 0xc3, (byte) 0x28});

        assertFailure(documentPath, Reason.INVALID_DOCUMENT);
    }

    @Test
    void rejectsAnUnsupportedDocumentType() throws Exception {
        Path documentPath = temporaryDirectory.resolve("notes.md");
        Files.writeString(documentPath, "private content", StandardCharsets.UTF_8);

        assertFailure(documentPath, Reason.INVALID_DOCUMENT);
    }

    @Test
    void rejectsADirectory() {
        assertFailure(temporaryDirectory, Reason.INVALID_DOCUMENT);
    }

    @Test
    void reportsAMissingDocumentAsUnavailable() {
        assertFailure(temporaryDirectory.resolve("missing.txt"), Reason.UNAVAILABLE);
    }

    @Test
    void rejectsASymbolicLinkWhenThePlatformSupportsCreation() throws Exception {
        Path target = temporaryDirectory.resolve("target.txt");
        Files.writeString(target, "private content", StandardCharsets.UTF_8);
        Path link = temporaryDirectory.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable to this test process.");
        }

        assertFailure(link, Reason.INVALID_DOCUMENT);
    }

    private void assertFailure(Path path, Reason reason) {
        KnowledgeIngestionException exception = assertThrows(
                KnowledgeIngestionException.class, () -> ingestor.ingest(path));
        assertEquals(reason, exception.reason());
        assertEquals("Local text document ingestion failed.", exception.getMessage());
    }
}
