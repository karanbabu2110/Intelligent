package io.kaos.conversation;

/** One immutable, role-safe message whose content remains exactly as supplied. */
public record ConversationMessage(ConversationRole role, String content) {
    public static final int MAX_CONTENT_CODE_POINTS = 65_536;

    public ConversationMessage {
        if (role == null) {
            throw new IllegalArgumentException("conversation message role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("conversation message content must not be null");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("conversation message content must not be blank");
        }
        if (content.codePointCount(0, content.length()) > MAX_CONTENT_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "conversation message content must contain at most "
                            + MAX_CONTENT_CODE_POINTS + " characters");
        }
    }
}
