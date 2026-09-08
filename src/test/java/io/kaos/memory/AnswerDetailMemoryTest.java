package io.kaos.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
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
        assertEquals(Optional.of(AnswerDetail.CONCISE), memory.retrieve());
    }

    @Test
    void retrievalDistinguishesAbsentAndPresentState() {
        AnswerDetailMemory memory = new AnswerDetailMemory();
        assertEquals(Optional.empty(), memory.retrieve());

        memory.create(AnswerDetailMemory.KEY, "balanced");

        assertEquals(Optional.of(AnswerDetail.BALANCED), memory.retrieve());
    }

    @Test
    void mapsEachValueToOneFixedAiInstruction() {
        assertEquals("Answer concisely and include only essential information.",
                AnswerDetail.CONCISE.aiInstruction());
        assertEquals("Balance brevity with enough explanation to make the answer clear.",
                AnswerDetail.BALANCED.aiInstruction());
        assertEquals("Answer in detail with relevant context and explanation.",
                AnswerDetail.DETAILED.aiInstruction());
    }
}
