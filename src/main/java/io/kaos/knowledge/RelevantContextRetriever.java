package io.kaos.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Ranks bounded stored chunks by deterministic cosine similarity. */
public final class RelevantContextRetriever {
    public static final int MAX_RESULTS = 3;

    public List<RetrievedContext> retrieve(double[] queryVector,
            String embeddingModel, List<StoredKnowledgeDocument> documents) {
        Objects.requireNonNull(queryVector, "queryVector");
        Objects.requireNonNull(embeddingModel, "embeddingModel");
        Objects.requireNonNull(documents, "documents");
        if (queryVector.length == 0 || queryVector.length > EmbeddedChunk.MAX_DIMENSIONS
                || java.util.Arrays.stream(queryVector).anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("query vector must be bounded and finite");
        }
        double queryMagnitude = magnitude(queryVector);
        if (queryMagnitude == 0.0) throw new IllegalArgumentException("query vector must be non-zero");

        List<RetrievedContext> matches = new ArrayList<>();
        for (StoredKnowledgeDocument document : List.copyOf(documents)) {
            if (!embeddingModel.equals(document.embeddingModel())) continue;
            for (EmbeddedChunk embedded : document.embeddedChunks()) {
                double[] vector = embedded.vector();
                if (vector.length != queryVector.length) continue;
                double candidateMagnitude = magnitude(vector);
                if (candidateMagnitude == 0.0) continue;
                double score = dot(queryVector, vector) / (queryMagnitude * candidateMagnitude);
                score = Math.max(-1.0, Math.min(1.0, score));
                matches.add(new RetrievedContext(
                        document.identifier(), embedded.chunk(), score));
            }
        }
        matches.sort(Comparator.comparingDouble(RetrievedContext::score).reversed()
                .thenComparingLong(RetrievedContext::documentIdentifier)
                .thenComparingInt(match -> match.chunk().index()));
        return List.copyOf(matches.subList(0, Math.min(MAX_RESULTS, matches.size())));
    }

    private static double dot(double[] left, double[] right) {
        double result = 0.0;
        for (int index = 0; index < left.length; index++) result += left[index] * right[index];
        return result;
    }

    private static double magnitude(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }
}
