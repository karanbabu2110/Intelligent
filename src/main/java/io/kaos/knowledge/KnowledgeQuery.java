package io.kaos.knowledge;

/** One bounded local retrieval query. */
public record KnowledgeQuery(String text) {
    public static final int MAX_CODE_POINTS = DocumentChunker.MAX_CHUNK_CODE_POINTS;

    public KnowledgeQuery {
        if (text == null) throw new IllegalArgumentException("query must not be null");
        if (text.isBlank()) throw new IllegalArgumentException("query must not be blank");
        int count = text.codePointCount(0, text.length());
        if (count > MAX_CODE_POINTS) throw new IllegalArgumentException("query is too long");
        if (text.codePoints().anyMatch(KnowledgeQuery::unsafe)) {
            throw new IllegalArgumentException("query contains unsupported control characters");
        }
    }

    public int codePointCount() { return text.codePointCount(0, text.length()); }

    private static boolean unsafe(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t';
    }
}
