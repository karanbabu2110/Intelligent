package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationHistoryTest {
    private static final ConversationMessage USER_MESSAGE =
            new ConversationMessage(ConversationRole.USER, "First question");
    private static final ConversationMessage ASSISTANT_MESSAGE =
            new ConversationMessage(ConversationRole.ASSISTANT, "First answer");

    @Test
    void startsEmpty() {
        ConversationHistory history = ConversationHistory.empty();

        assertTrue(history.messages().isEmpty());
    }

    @Test
    void preservesMessageInsertionOrder() {
        ConversationHistory history = ConversationHistory.empty()
                .append(USER_MESSAGE)
                .append(ASSISTANT_MESSAGE);

        assertEquals(List.of(USER_MESSAGE, ASSISTANT_MESSAGE), history.messages());
    }

    @Test
    void appendingLeavesTheEarlierSnapshotUnchanged() {
        ConversationHistory original = ConversationHistory.empty().append(USER_MESSAGE);

        ConversationHistory appended = original.append(ASSISTANT_MESSAGE);

        assertEquals(List.of(USER_MESSAGE), original.messages());
        assertEquals(List.of(USER_MESSAGE, ASSISTANT_MESSAGE), appended.messages());
    }

    @Test
    void defensivelyCopiesCallerOwnedInput() {
        List<ConversationMessage> callerMessages = new ArrayList<>(List.of(USER_MESSAGE));
        ConversationHistory history = new ConversationHistory(callerMessages);

        callerMessages.add(ASSISTANT_MESSAGE);

        assertEquals(List.of(USER_MESSAGE), history.messages());
    }

    @Test
    void exposesAnUnmodifiableMessageList() {
        ConversationHistory history = ConversationHistory.empty().append(USER_MESSAGE);

        assertThrows(UnsupportedOperationException.class,
                () -> history.messages().add(ASSISTANT_MESSAGE));
    }

    @Test
    void rejectsNullInputsWithContentFreeDiagnostics() {
        IllegalArgumentException nullList = assertThrows(
                IllegalArgumentException.class, () -> new ConversationHistory(null));
        IllegalArgumentException nullEntry = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversationHistory(java.util.Arrays.asList(USER_MESSAGE, null)));
        IllegalArgumentException nullAppend = assertThrows(
                IllegalArgumentException.class,
                () -> ConversationHistory.empty().append(null));

        assertEquals("conversation history messages must not be null", nullList.getMessage());
        assertEquals(
                "conversation history messages must not contain null entries",
                nullEntry.getMessage());
        assertEquals("conversation history message must not be null", nullAppend.getMessage());
    }

    @Test
    void acceptsTheExactMessageLimitAndRejectsAnotherAppend() {
        ConversationHistory history = new ConversationHistory(
                java.util.Collections.nCopies(ConversationHistory.MAX_MESSAGES, USER_MESSAGE));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> history.append(ASSISTANT_MESSAGE));

        assertEquals(ConversationHistory.MAX_MESSAGES, history.messages().size());
        assertEquals("conversation history has reached its message limit",
                exception.getMessage());
    }

    @Test
    void rejectsConstructionBeyondTheMessageLimitWithoutExposingContent() {
        ConversationMessage privateMessage =
                new ConversationMessage(ConversationRole.USER, "private message content");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversationHistory(java.util.Collections.nCopies(
                        ConversationHistory.MAX_MESSAGES + 1, privateMessage)));

        assertEquals("conversation history must contain at most "
                + ConversationHistory.MAX_MESSAGES + " messages", exception.getMessage());
        assertFalse(exception.getMessage().contains(privateMessage.content()));
    }
}
