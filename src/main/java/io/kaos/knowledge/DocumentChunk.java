package io.kaos.knowledge;

import java.util.Objects;

/** One immutable retrieval unit from an exact extracted-text snapshot. */
public record DocumentChunk(
        String sourceName,
        int index,
        int startCodePoint,
        int endCodePoint,
        String content) {
    public DocumentChunk {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(content, "content");
        if (sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        if (startCodePoint < 0 || endCodePoint <= startCodePoint) {
            throw new IllegalArgumentException("code-point range must be non-empty and ordered");
        }
        int codePointCount = content.codePointCount(0, content.length());
        if (codePointCount != endCodePoint - startCodePoint
                || codePointCount > DocumentChunker.MAX_CHUNK_CODE_POINTS) {
            throw new IllegalArgumentException("content must match the bounded code-point range");
        }
    }

    /** Returns the exact number of Unicode code points in this chunk. */
    public int codePointCount() {
        return endCodePoint - startCodePoint;
    }
}
