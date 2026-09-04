package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SourceCitationTest {
    @Test
    void rendersAStableBoundedLabel() {
        SourceCitation citation = new SourceCitation(2, 7, "notes.txt", 4);

        assertEquals("[2]", citation.label());
        assertEquals(7, citation.documentIdentifier());
        assertEquals("notes.txt", citation.sourceName());
        assertEquals(4, citation.chunkIndex());
    }

    @Test
    void escapesSourceControlsAndBackslashesForSingleLineDiagnostics() {
        SourceCitation citation = new SourceCitation(1, 7, "folder\\notes\r\n\t.txt", 0);

        assertEquals("folder\\\\notes\\r\\n\\t.txt", citation.printableSourceName());
    }

    @Test
    void rejectsInvalidSourceCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new SourceCitation(0, 7, "notes.txt", 4));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceCitation(1, 0, "notes.txt", 4));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceCitation(1, 7, " ", 4));
        assertThrows(IllegalArgumentException.class,
                () -> new SourceCitation(1, 7, "notes.txt", -1));
    }
}
