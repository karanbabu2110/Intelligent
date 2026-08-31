package io.kaos.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns multiple selectable conversation histories for one foreground process. */
public final class ConversationSession {
    public static final int MAX_CONVERSATIONS = 8;
    public static final int MAX_TURNS_PER_CONVERSATION = ConversationHistory.MAX_MESSAGES / 2;
    private final Map<Long, ConversationHistory> histories = new LinkedHashMap<>();
    private long nextIdentifier = 1;
    private long activeIdentifier;

    /** Creates, selects, and returns the next deterministic conversation identifier. */
    public long create() {
        if (!canCreate()) {
            throw new IllegalStateException(
                    "conversation session has reached its conversation limit");
        }
        if (nextIdentifier == Long.MAX_VALUE) {
            throw new IllegalStateException("conversation identifier space is exhausted");
        }
        long identifier = nextIdentifier++;
        histories.put(identifier, ConversationHistory.empty());
        activeIdentifier = identifier;
        return identifier;
    }

    /** Returns whether another conversation can be created without exceeding the limit. */
    public boolean canCreate() {
        return histories.size() < MAX_CONVERSATIONS;
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

    /** Returns whether the selected history has room for one complete clean turn. */
    public boolean canAppendTurn() {
        return requireActiveHistory().messages().size() <= ConversationHistory.MAX_MESSAGES - 2;
    }

    /** Atomically appends one clean user and assistant turn to the selected history. */
    public void appendTurn(String userContent, String assistantContent) {
        if (!canAppendTurn()) {
            throw new IllegalStateException(
                    "active conversation has reached its turn limit");
        }
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
