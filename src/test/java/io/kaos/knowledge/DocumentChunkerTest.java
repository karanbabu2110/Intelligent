package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void keepsShortExactTextInOneChunk() {
        String content = "\ufeff first\r\nsecond\t ";

        List<DocumentChunk> chunks = chunker.chunk(new ExtractedText("notes.txt", content));

        assertEquals(1, chunks.size());
        assertChunk(chunks.get(0), "notes.txt", 0, 0,
                content.codePointCount(0, content.length()), content);
    }

    @Test
    void createsDeterministicOverlappingChunksWithoutSplittingUnicodeCodePoints() {
        String content = "a".repeat(799) + "😀" + "b".repeat(999) + "\r\nend";
        ExtractedText extractedText = new ExtractedText("unicode.txt", content);

        List<DocumentChunk> chunks = chunker.chunk(extractedText);

        assertEquals(3, chunks.size());
        assertChunk(chunks.get(0), "unicode.txt", 0, 0, 1_000,
                codePointSubstring(content, 0, 1_000));
        assertChunk(chunks.get(1), "unicode.txt", 1, 800, 1_800,
                codePointSubstring(content, 800, 1_800));
        assertChunk(chunks.get(2), "unicode.txt", 2, 1_600, 1_804,
                codePointSubstring(content, 1_600, 1_804));
        assertEquals(
                codePointSubstring(content, 800, 1_000),
                codePointSubstring(chunks.get(0).content(), 800, 1_000));
        assertEquals(
                codePointSubstring(content, 800, 1_000),
                codePointSubstring(chunks.get(1).content(), 0, 200));
    }

    @Test
    void returnsAnImmutableChunkList() {
        List<DocumentChunk> chunks = chunker.chunk(new ExtractedText("notes.txt", "content"));

        assertThrows(UnsupportedOperationException.class, () -> chunks.clear());
    }

    @Test
    void boundsTheChunkCountAtTheExistingExtractedTextLimit() {
        List<DocumentChunk> chunks = chunker.chunk(new ExtractedText(
                "maximum.txt", "x".repeat(ExtractedText.MAX_CODE_POINTS)));

        assertEquals(DocumentChunker.MAX_CHUNKS, chunks.size());
        assertEquals(ExtractedText.MAX_CODE_POINTS,
                chunks.get(chunks.size() - 1).endCodePoint());
    }

    @Test
    void rejectsMissingExtractedText() {
        assertThrows(NullPointerException.class, () -> chunker.chunk(null));
    }

    @Test
    void rejectsChunkMetadataThatDoesNotMatchItsContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentChunk("notes.txt", 0, 10, 12, "x"));
    }

    private static void assertChunk(
            DocumentChunk chunk,
            String sourceName,
            int index,
            int start,
            int end,
            String content) {
        assertEquals(sourceName, chunk.sourceName());
        assertEquals(index, chunk.index());
        assertEquals(start, chunk.startCodePoint());
        assertEquals(end, chunk.endCodePoint());
        assertEquals(end - start, chunk.codePointCount());
        assertEquals(content, chunk.content());
    }

    private static String codePointSubstring(String content, int start, int end) {
        int startCharacter = content.offsetByCodePoints(0, start);
        int endCharacter = content.offsetByCodePoints(startCharacter, end - start);
        return content.substring(startCharacter, endCharacter);
    }
}
