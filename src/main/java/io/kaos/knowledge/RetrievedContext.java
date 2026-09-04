package io.kaos.knowledge;

import java.util.Objects;

/** One immutable stored chunk ranked for a query. */
public record RetrievedContext(long documentIdentifier, DocumentChunk chunk, double score) {
    public RetrievedContext {
        if (documentIdentifier <= 0) throw new IllegalArgumentException("documentIdentifier must be positive");
        Objects.requireNonNull(chunk, "chunk");
        if (!Double.isFinite(score) || score < -1.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be a finite cosine similarity");
        }
    }
}
