package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.knowledge.TextExtractionException.Reason;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlainTextExtractorTest {
    private final PlainTextExtractor extractor = new PlainTextExtractor();

    @Test
    void extractsExactMultilingualTextWithoutNormalizingContent() throws Exception {
        String expected = "\ufeff KAOS café 😀\r\nsecond line\t ";
        IngestedDocument document = document(
                "notes.txt", TextDocumentIngestor.MEDIA_TYPE,
                expected.getBytes(StandardCharsets.UTF_8));

        ExtractedText extracted = extractor.extract(document);

        assertEquals("notes.txt", extracted.sourceName());
        assertEquals(expected, extracted.content());
        assertEquals(expected.codePointCount(0, expected.length()), extracted.codePointCount());
    }

    @Test
    void countsUnicodeCodePointsRatherThanUtf16Units() throws Exception {
        ExtractedText extracted = extractor.extract(document(
                "emoji.txt", TextDocumentIngestor.MEDIA_TYPE,
                "A😀B".getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, extracted.codePointCount());
    }

    @Test
    void rejectsAnUnsupportedMediaTypeWithoutContentDetails() {
        assertFailure(document(
                "notes.md", "text/markdown", "private text".getBytes(StandardCharsets.UTF_8)),
                Reason.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void rejectsMalformedUtf8WithoutContentDetails() {
        assertFailure(document(
                "notes.txt", TextDocumentIngestor.MEDIA_TYPE,
                new byte[] {(byte) 0xc3, (byte) 0x28}),
                Reason.INVALID_TEXT);
    }

    @Test
    void rejectsEmptyContent() {
        assertFailure(document(
                "notes.txt", TextDocumentIngestor.MEDIA_TYPE, new byte[0]),
                Reason.INVALID_TEXT);
    }

    @Test
    void rejectsContentAboveTheAdmissionLimit() {
        assertFailure(document(
                "notes.txt", TextDocumentIngestor.MEDIA_TYPE,
                new byte[TextDocumentIngestor.MAX_DOCUMENT_BYTES + 1]),
                Reason.TOO_LARGE);
    }

    private static IngestedDocument document(String name, String mediaType, byte[] content) {
        return new IngestedDocument(name, mediaType, content);
    }

    private void assertFailure(IngestedDocument document, Reason reason) {
        TextExtractionException exception = assertThrows(
                TextExtractionException.class, () -> extractor.extract(document));
        assertEquals(reason, exception.reason());
        assertEquals("Admitted document text extraction failed.", exception.getMessage());
    }
}
