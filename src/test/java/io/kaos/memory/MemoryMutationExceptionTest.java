package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MemoryMutationExceptionTest {
    @Test
    void exposesOnlyAStableReasonAndMessage() {
        MemoryMutationException exception = MemoryMutationException.absent();

        assertEquals(MemoryMutationException.Reason.ABSENT, exception.reason());
        assertEquals("Memory mutation failed.", exception.getMessage());
    }
}
