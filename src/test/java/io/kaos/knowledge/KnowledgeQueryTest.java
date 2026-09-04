package io.kaos.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KnowledgeQueryTest {
    @Test
    void preservesBoundedUnicodeQueryExactly() {
        KnowledgeQuery query = new KnowledgeQuery("  café 😀?  ");
        assertEquals("  café 😀?  ", query.text());
        assertEquals(11, query.codePointCount());
    }

    @Test
    void rejectsBlankOversizedAndUnsafeQueries() {
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeQuery(" \t"));
        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeQuery("a".repeat(KnowledgeQuery.MAX_CODE_POINTS + 1)));
        assertThrows(IllegalArgumentException.class, () -> new KnowledgeQuery("safe\u0000private"));
    }
}
