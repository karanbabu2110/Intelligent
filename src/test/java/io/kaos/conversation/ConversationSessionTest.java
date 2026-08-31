package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationSessionTest {
    @Test
    void createsAndSelectsDeterministicIdentifiers() {
        ConversationSession session = new ConversationSession();

        long first = session.create();
        long second = session.create();

        assertEquals(1, first);
        assertEquals(2, second);
        assertEquals(List.of(1L, 2L), session.conversationIdentifiers());
        assertEquals(second, session.activeIdentifier());
        assertTrue(session.activeHistory().messages().isEmpty());
    }

    @Test
    void storesACompleteTurnInOrder() {
        ConversationSession session = new ConversationSession();
        session.create();

        session.appendTurn("First question", "First answer");

        assertEquals(List.of(
                new ConversationMessage(ConversationRole.USER, "First question"),
                new ConversationMessage(ConversationRole.ASSISTANT, "First answer")),
                session.activeHistory().messages());
    }

    @Test
    void switchingConversationsKeepsTheirHistoriesIsolated() {
        ConversationSession session = new ConversationSession();
        long first = session.create();
        session.appendTurn("First question", "First answer");
        long second = session.create();
        session.appendTurn("Second question", "Second answer");

        assertTrue(session.select(first));
        assertEquals("First question", session.activeHistory().messages().get(0).content());
        assertTrue(session.select(second));
        assertEquals("Second question", session.activeHistory().messages().get(0).content());
    }

    @Test
    void unknownSelectionLeavesTheActiveConversationUnchanged() {
        ConversationSession session = new ConversationSession();
        long active = session.create();

        assertFalse(session.select(999));
        assertFalse(session.select(0));
        assertEquals(active, session.activeIdentifier());
    }

    @Test
    void activeOperationsRequireACreatedConversation() {
        ConversationSession session = new ConversationSession();

        IllegalStateException identifierFailure = assertThrows(
                IllegalStateException.class, session::activeIdentifier);
        IllegalStateException historyFailure = assertThrows(
                IllegalStateException.class, session::activeHistory);
        IllegalStateException appendFailure = assertThrows(
                IllegalStateException.class,
                () -> session.appendTurn("private user content", "private assistant content"));

        assertEquals("conversation session has no active conversation",
                identifierFailure.getMessage());
        assertEquals("conversation session has no active conversation", historyFailure.getMessage());
        assertEquals("conversation session has no active conversation", appendFailure.getMessage());
        assertFalse(appendFailure.getMessage().contains("private"));
    }

    @Test
    void rejectsAConversationBeyondTheLimitWithoutChangingSelectionOrSequence() {
        ConversationSession session = new ConversationSession();
        for (int expected = 1; expected <= ConversationSession.MAX_CONVERSATIONS; expected++) {
            assertEquals(expected, session.create());
        }

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, session::create);

        assertFalse(session.canCreate());
        assertEquals(ConversationSession.MAX_CONVERSATIONS, session.activeIdentifier());
        assertEquals(ConversationSession.MAX_CONVERSATIONS,
                session.conversationIdentifiers().size());
        assertEquals("conversation session has reached its conversation limit",
                exception.getMessage());
    }

    @Test
    void rejectsATurnBeyondTheLimitWithoutChangingHistoryOrExposingContent() {
        ConversationSession session = new ConversationSession();
        session.create();
        for (int turn = 0; turn < ConversationSession.MAX_TURNS_PER_CONVERSATION; turn++) {
            session.appendTurn("Question " + turn, "Answer " + turn);
        }

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> session.appendTurn("private rejected question", "private rejected answer"));

        assertFalse(session.canAppendTurn());
        assertEquals(ConversationHistory.MAX_MESSAGES, session.activeHistory().messages().size());
        assertEquals("active conversation has reached its turn limit", exception.getMessage());
        assertFalse(exception.getMessage().contains("private"));
    }
}
