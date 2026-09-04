package io.kaos.knowledge;

import java.util.List;

/** One bounded document snapshot restored from local knowledge storage. */
public record StoredKnowledgeDocument(long identifier, String embeddingModel,
        List<EmbeddedChunk> embeddedChunks) {
    public StoredKnowledgeDocument {
        if (identifier <= 0) throw new IllegalArgumentException("identifier must be positive");
        if (embeddingModel == null || embeddingModel.isBlank() || embeddingModel.length() > 128) {
            throw new IllegalArgumentException("embeddingModel must be bounded and non-blank");
        }
        embeddedChunks = List.copyOf(embeddedChunks);
        if (embeddedChunks.isEmpty() || embeddedChunks.size() > DocumentChunker.MAX_CHUNKS) {
            throw new IllegalArgumentException("embeddedChunks must be bounded and non-empty");
        }
    }
}
