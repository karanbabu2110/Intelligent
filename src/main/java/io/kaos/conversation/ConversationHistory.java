package io.kaos.conversation;

import java.util.ArrayList;
import java.util.List;

/** An immutable, ordered in-memory snapshot of conversation messages. */
public record ConversationHistory(List<ConversationMessage> messages) {
    public ConversationHistory {
        if (messages == null) {
            throw new IllegalArgumentException("conversation history messages must not be null");
        }
        if (messages.stream().anyMatch(message -> message == null)) {
            throw new IllegalArgumentException(
                    "conversation history messages must not contain null entries");
        }
        messages = List.copyOf(messages);
    }

    /** Creates a history containing no messages. */
    public static ConversationHistory empty() {
        return new ConversationHistory(List.of());
    }

    /** Returns a new ordered history with the supplied message appended. */
    public ConversationHistory append(ConversationMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("conversation history message must not be null");
        }

        List<ConversationMessage> appendedMessages = new ArrayList<>(messages);
        appendedMessages.add(message);
        return new ConversationHistory(appendedMessages);
    }
}
