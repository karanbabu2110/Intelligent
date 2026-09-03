package io.kaos.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Splits exact extracted text into bounded, overlapping retrieval units. */
public final class DocumentChunker {
    public static final int MAX_CHUNK_CODE_POINTS = 1_000;
    public static final int OVERLAP_CODE_POINTS = 200;

    private static final int CHUNK_ADVANCE_CODE_POINTS =
            MAX_CHUNK_CODE_POINTS - OVERLAP_CODE_POINTS;
    public static final int MAX_CHUNKS = 1 +
            (ExtractedText.MAX_CODE_POINTS - MAX_CHUNK_CODE_POINTS
                    + CHUNK_ADVANCE_CODE_POINTS - 1) / CHUNK_ADVANCE_CODE_POINTS;

    /** Returns an immutable, ordered chunk list without normalizing the source text. */
    public List<DocumentChunk> chunk(ExtractedText extractedText) {
        Objects.requireNonNull(extractedText, "extractedText");
        String content = extractedText.content();
        int totalCodePoints = extractedText.codePointCount();
        List<DocumentChunk> chunks = new ArrayList<>();
        int startCodePoint = 0;
        int index = 0;

        while (startCodePoint < totalCodePoints) {
            int endCodePoint = Math.min(
                    totalCodePoints, startCodePoint + MAX_CHUNK_CODE_POINTS);
            int startCharacter = content.offsetByCodePoints(0, startCodePoint);
            int endCharacter = content.offsetByCodePoints(startCharacter,
                    endCodePoint - startCodePoint);
            chunks.add(new DocumentChunk(
                    extractedText.sourceName(),
                    index,
                    startCodePoint,
                    endCodePoint,
                    content.substring(startCharacter, endCharacter)));
            if (endCodePoint == totalCodePoints) {
                break;
            }
            startCodePoint += CHUNK_ADVANCE_CODE_POINTS;
            index++;
        }
        return List.copyOf(chunks);
    }
}
