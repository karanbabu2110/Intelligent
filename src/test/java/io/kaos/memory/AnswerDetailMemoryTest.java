package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AnswerDetailMemoryTest {
    @Test
    void createsEachSupportedValueExactly() {
        assertEquals(AnswerDetail.CONCISE,
                new AnswerDetailMemory().create(AnswerDetailMemory.KEY, "concise"));
        assertEquals(AnswerDetail.BALANCED,
                new AnswerDetailMemory().create(AnswerDetailMemory.KEY, "balanced"));
        assertEquals(AnswerDetail.DETAILED,
                new AnswerDetailMemory().create(AnswerDetailMemory.KEY, "detailed"));
    }

    @Test
    void rejectsUnknownKeyWithoutCreatingTheMemory() {
        AnswerDetailMemory memory = new AnswerDetailMemory();

        MemoryCreationException exception = assertThrows(
                MemoryCreationException.class, () -> memory.create("private-note", "concise"));

        assertEquals(MemoryCreationException.Reason.INVALID_KEY, exception.reason());
        assertEquals(AnswerDetail.DETAILED,
                memory.create(AnswerDetailMemory.KEY, "detailed"));
    }

    @Test
    void rejectsUnknownValueWithoutCreatingTheMemory() {
        AnswerDetailMemory memory = new AnswerDetailMemory();

        MemoryCreationException exception = assertThrows(
                MemoryCreationException.class,
                () -> memory.create(AnswerDetailMemory.KEY, "unbounded private instruction"));

        assertEquals(MemoryCreationException.Reason.INVALID_VALUE, exception.reason());
        assertEquals(AnswerDetail.CONCISE,
                memory.create(AnswerDetailMemory.KEY, "concise"));
    }

    @Test
    void rejectsCreationWhenTheMemoryAlreadyExistsWithoutOverwritingIt() {
        AnswerDetailMemory memory = new AnswerDetailMemory();
        assertEquals(AnswerDetail.CONCISE,
                memory.create(AnswerDetailMemory.KEY, "concise"));

        MemoryCreationException exception = assertThrows(
                MemoryCreationException.class,
                () -> memory.create(AnswerDetailMemory.KEY, "detailed"));

        assertEquals(MemoryCreationException.Reason.ALREADY_EXISTS, exception.reason());
    }
}
