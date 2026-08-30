package io.kaos.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns multiple selectable conversation histories for one foreground process. */
public final class ConversationSession {
    private final Map<Long, ConversationHistory> histories = new LinkedHashMap<>();
    private long nextIdentifier = 1;
    private long activeIdentifier;

    /** Creates, selects, and returns the next deterministic conversation identifier. */
    public long create() {
        if (nextIdentifier == Long.MAX_VALUE) {
            throw new IllegalStateException("conversation identifier space is exhausted");
        }
        long identifier = nextIdentifier++;
        histories.put(identifier, ConversationHistory.empty());
        activeIdentifier = identifier;
        return identifier;
    }

    /** Selects an existing conversation without changing state when it does not exist. */
    public boolean select(long identifier) {
        if (!histories.containsKey(identifier)) {
            return false;
        }
        activeIdentifier = identifier;
        return true;
    }

    /** Returns identifiers in creation order. */
    public List<Long> conversationIdentifiers() {
        return List.copyOf(histories.keySet());
    }

    /** Returns the currently selected identifier. */
    public long activeIdentifier() {
        requireActiveHistory();
        return activeIdentifier;
    }

    /** Returns the immutable history snapshot for the selected conversation. */
    public ConversationHistory activeHistory() {
        return requireActiveHistory();
    }

    /** Atomically appends one clean user and assistant turn to the selected history. */
    public void appendTurn(String userContent, String assistantContent) {
        ConversationMessage user =
                new ConversationMessage(ConversationRole.USER, userContent);
        ConversationMessage assistant =
                new ConversationMessage(ConversationRole.ASSISTANT, assistantContent);
        ConversationHistory updated = requireActiveHistory().append(user).append(assistant);
        histories.put(activeIdentifier, updated);
    }

    private ConversationHistory requireActiveHistory() {
        ConversationHistory history = histories.get(activeIdentifier);
        if (history == null) {
            throw new IllegalStateException("conversation session has no active conversation");
        }
        return history;
    }
}
