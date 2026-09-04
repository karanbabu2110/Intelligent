package io.kaos.app;

import io.kaos.knowledge.EmbeddedChunk;
import java.util.List;

/** Narrow application seam for atomic knowledge persistence. */
@FunctionalInterface
interface KnowledgeStorageSubmission {
    long store(String embeddingModel, List<EmbeddedChunk> chunks);
}
