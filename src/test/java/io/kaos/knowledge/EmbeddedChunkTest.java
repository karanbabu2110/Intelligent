package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmbeddedChunkTest {
    @Test
    void defensivelyCopiesFiniteVectorValues() {
        DocumentChunk chunk = new DocumentChunk("notes.txt", 0, 0, 4, "text");
        double[] values = {0.25, -0.5};
        EmbeddedChunk embedded = new EmbeddedChunk(chunk, values);
        values[0] = 9;
        double[] returned = embedded.vector();
        returned[1] = 9;

        assertEquals(0.25, embedded.vector()[0]);
        assertEquals(-0.5, embedded.vector()[1]);
        assertEquals(2, embedded.dimensions());
    }

    @Test
    void rejectsNonFiniteAndEmptyVectors() {
        DocumentChunk chunk = new DocumentChunk("notes.txt", 0, 0, 4, "text");
        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddedChunk(chunk, new double[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddedChunk(chunk, new double[] {Double.NaN}));
    }
}
