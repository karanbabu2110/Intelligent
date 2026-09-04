package io.kaos.app;

import io.kaos.knowledge.StoredKnowledgeDocument;
import java.util.List;

/** Narrow application seam for bounded retrieval reads. */
@FunctionalInterface
interface KnowledgeDocumentLoader {
    List<StoredKnowledgeDocument> load();
}
