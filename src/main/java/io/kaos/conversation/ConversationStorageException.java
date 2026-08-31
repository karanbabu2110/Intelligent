package io.kaos.conversation;

/**
 * Reports a local conversation storage failure without exposing database details.
 */
public final class ConversationStorageException extends RuntimeException {

    ConversationStorageException(String message) {
        super(message);
    }
}
