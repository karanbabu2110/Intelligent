package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RelevantContextRetrieverTest {
    @Test
    void returnsTheThreeMostSimilarCompatibleChunksWithDeterministicTies() {
        List<StoredKnowledgeDocument> documents = List.of(
                document(1, "model", embedded("first", 0, 1, 0), embedded("second", 1, 0.8, 0.2)),
                document(2, "other-model", embedded("ignored", 0, 1, 0)),
                document(3, "model", embedded("third", 0, 0, 1), embedded("fourth", 1, 1, 0)));

        List<RetrievedContext> result = new RelevantContextRetriever().retrieve(
                new double[] {1, 0}, "model", documents);

        assertEquals(List.of("first", "fourth", "second"), result.stream()
                .map(match -> match.chunk().content()).toList());
        assertEquals(List.of(1L, 3L, 1L), result.stream()
                .map(RetrievedContext::documentIdentifier).toList());
        assertTrue(result.get(0).score() >= result.get(1).score());
        assertTrue(result.get(1).score() >= result.get(2).score());
    }

    @Test
    void skipsDimensionMismatchesAndZeroVectors() {
        List<RetrievedContext> result = new RelevantContextRetriever().retrieve(
                new double[] {1, 0}, "model", List.of(
                        document(1, "model", embedded("zero", 0, 0, 0)),
                        document(2, "model", new EmbeddedChunk(
                                new DocumentChunk("notes.txt", 0, 0, 5, "three"),
                                new double[] {1, 0, 0}))));
        assertTrue(result.isEmpty());
    }

    private static StoredKnowledgeDocument document(long identifier, String model,
            EmbeddedChunk... chunks) {
        return new StoredKnowledgeDocument(identifier, model, List.of(chunks));
    }

    private static EmbeddedChunk embedded(String content, int index, double... vector) {
        return new EmbeddedChunk(new DocumentChunk(
                "notes.txt", index, index * content.length(),
                (index + 1) * content.length(), content), vector);
    }
}
