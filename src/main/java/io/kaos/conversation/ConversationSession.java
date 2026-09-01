package io.kaos.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns multiple selectable conversation histories for one foreground process. */
public final class ConversationSession {
    public static final int MAX_CONVERSATIONS = 8;
    public static final int MAX_TURNS_PER_CONVERSATION = ConversationHistory.MAX_MESSAGES / 2;
    private final Map<Long, ConversationHistory> histories = new LinkedHashMap<>();
    private long nextIdentifier = 1;
    private long activeIdentifier;

    /** Restores a bounded working set in creation order and selects its newest entry. */
    public static ConversationSession restore(Map<Long, ConversationHistory> restoredHistories) {
        Objects.requireNonNull(restoredHistories, "restoredHistories");
        long greatestIdentifier = restoredHistories.keySet().stream()
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
        return restore(restoredHistories, greatestIdentifier);
    }

    /** Restores a working set while continuing after every identifier in durable storage. */
    public static ConversationSession restore(
            Map<Long, ConversationHistory> restoredHistories,
            long greatestKnownIdentifier) {
        Objects.requireNonNull(restoredHistories, "restoredHistories");
        if (greatestKnownIdentifier < 0) {
            throw new IllegalArgumentException("greatest known identifier must not be negative");
        }
        if (restoredHistories.size() > MAX_CONVERSATIONS) {
            throw new IllegalArgumentException(
                    "restored histories exceed the conversation session limit");
        }

        ConversationSession session = new ConversationSession();
        for (Map.Entry<Long, ConversationHistory> entry : restoredHistories.entrySet()) {
            Long identifier = entry.getKey();
            ConversationHistory history = entry.getValue();
            if (identifier == null || identifier <= 0 || history == null) {
                throw new IllegalArgumentException("restored conversation state is invalid");
            }
            requireCleanTurns(history);
            session.histories.put(identifier, history);
            session.activeIdentifier = identifier;
            if (identifier > greatestKnownIdentifier) {
                throw new IllegalArgumentException(
                        "restored identifier exceeds the durable identifier boundary");
            }
        }
        session.nextIdentifier = greatestKnownIdentifier == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : greatestKnownIdentifier + 1;
        return session;
    }

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

    private static void requireCleanTurns(ConversationHistory history) {
        List<ConversationMessage> messages = history.messages();
        if (messages.size() % 2 != 0) {
            throw new IllegalArgumentException("restored conversation history is incomplete");
        }
        for (int index = 0; index < messages.size(); index++) {
            ConversationRole expected = index % 2 == 0
                    ? ConversationRole.USER
                    : ConversationRole.ASSISTANT;
            if (messages.get(index).role() != expected) {
                throw new IllegalArgumentException("restored conversation history order is invalid");
            }
        }
    }
}
