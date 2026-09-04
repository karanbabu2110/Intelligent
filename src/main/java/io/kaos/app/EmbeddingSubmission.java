package io.kaos.app;

import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.knowledge.DocumentChunk;
import java.util.List;

/** Narrow application seam for deterministic knowledge-command testing. */
@FunctionalInterface
interface EmbeddingSubmission {
    OllamaEmbeddingClient.Result embed(
            OllamaEmbeddingConfiguration configuration, List<DocumentChunk> chunks);
}
