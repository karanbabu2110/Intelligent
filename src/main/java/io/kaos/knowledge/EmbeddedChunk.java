package io.kaos.knowledge;

import java.util.Arrays;
import java.util.Objects;

/** One immutable document chunk paired with its bounded embedding vector. */
public record EmbeddedChunk(DocumentChunk chunk, double[] vector) {
    public static final int MAX_DIMENSIONS = 4_096;

    public EmbeddedChunk {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(vector, "vector");
        if (vector.length == 0 || vector.length > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("vector dimensions must be bounded and non-empty");
        }
        if (Arrays.stream(vector).anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("vector values must be finite");
        }
        vector = vector.clone();
    }

    @Override
    public double[] vector() {
        return vector.clone();
    }

    /** Returns the number of numeric dimensions without exposing vector values. */
    public int dimensions() {
        return vector.length;
    }
}
