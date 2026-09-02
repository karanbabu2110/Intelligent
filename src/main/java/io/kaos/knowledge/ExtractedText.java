package io.kaos.knowledge;

import java.util.Objects;

/** One immutable, bounded text snapshot extracted from an admitted document. */
public record ExtractedText(String sourceName, String content) {
    public static final int MAX_CODE_POINTS = TextDocumentIngestor.MAX_DOCUMENT_BYTES;

    public ExtractedText {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(content, "content");
        if (sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        int codePointCount = content.codePointCount(0, content.length());
        if (codePointCount == 0 || codePointCount > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("content must be within the text limit");
        }
    }

    /** Returns the number of Unicode code points in the exact extracted text. */
    public int codePointCount() {
        return content.codePointCount(0, content.length());
    }
}
