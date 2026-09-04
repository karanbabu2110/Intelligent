package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Deterministic evaluation across extraction, chunking, storage, retrieval, and grounding. */
class RagPipelineEvaluationTest {
    private static final String MODEL = "evaluation-model";

    @TempDir Path temporaryDirectory;

    @Test
    void preservesRankedEvidenceAndAttributionAcrossTheLocalKnowledgePath() throws Exception {
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(
                temporaryDirectory.resolve("knowledge.db"));
        long relevantDocument = store.store(MODEL, embed(
                "policy.txt", "Backups run every night.", 1, 0));
        long distractorDocument = store.store(MODEL, embed(
                "notes.txt", "Lunch starts at noon.", 0, 1));

        List<RetrievedContext> contexts = new RelevantContextRetriever().retrieve(
                new double[] {1, 0}, MODEL, store.loadAll());
        GroundedPrompt prompt = new GroundedPromptBuilder().build(
                new KnowledgeQuery("When do backups run?"), contexts);

        assertEquals(List.of(relevantDocument, distractorDocument), contexts.stream()
                .map(RetrievedContext::documentIdentifier).toList());
        assertEquals(List.of(
                new SourceCitation(1, relevantDocument, "policy.txt", 0),
                new SourceCitation(2, distractorDocument, "notes.txt", 0)),
                prompt.citations());
        assertTrue(prompt.text().contains("CITATION: [1]\nDOCUMENT: " + relevantDocument));
        assertTrue(prompt.text().contains("CONTENT:\nBackups run every night."));
        assertTrue(prompt.text().contains("Cite every supported claim"));
    }

    @Test
    void excludesStoredVectorsFromAnotherModel() throws Exception {
        SqliteKnowledgeStore store = new SqliteKnowledgeStore(
                temporaryDirectory.resolve("incompatible.db"));
        store.store("different-model", embed("private.txt", "private fact", 1, 0));

        List<RetrievedContext> contexts = new RelevantContextRetriever().retrieve(
                new double[] {1, 0}, MODEL, store.loadAll());

        assertTrue(contexts.isEmpty());
    }

    private static List<EmbeddedChunk> embed(String source, String content, double... vector)
            throws TextExtractionException {
        IngestedDocument document = new IngestedDocument(source,
                TextDocumentIngestor.MEDIA_TYPE, content.getBytes(StandardCharsets.UTF_8));
        List<DocumentChunk> chunks = new DocumentChunker().chunk(
                new PlainTextExtractor().extract(document));
        return chunks.stream().map(chunk -> new EmbeddedChunk(chunk, vector)).toList();
    }
}
