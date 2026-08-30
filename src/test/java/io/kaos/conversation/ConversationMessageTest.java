package io.kaos.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ConversationMessageTest {
    @Test
    void representsTheCurrentlySupportedConversationRoles() {
        ConversationMessage userMessage =
                new ConversationMessage(ConversationRole.USER, "What can KAOS do?");
        ConversationMessage assistantMessage =
                new ConversationMessage(ConversationRole.ASSISTANT, "KAOS can answer locally.");

        assertEquals(ConversationRole.USER, userMessage.role());
        assertEquals(ConversationRole.ASSISTANT, assistantMessage.role());
    }

    @Test
    void preservesValidContentExactly() {
        String content = "  First line 🌍\n\tsecond line  ";

        ConversationMessage message =
                new ConversationMessage(ConversationRole.ASSISTANT, content);

        assertEquals(content, message.content());
    }

    @Test
    void rejectsANullRoleWithoutIncludingMessageContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversationMessage(null, "private message"));

        assertEquals("conversation message role must not be null", exception.getMessage());
    }

    @Test
    void rejectsNullContent() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversationMessage(ConversationRole.USER, null));

        assertEquals("conversation message content must not be null", exception.getMessage());
    }

    @Test
    void rejectsBlankContentWithoutIncludingItInTheDiagnostic() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ConversationMessage(ConversationRole.USER, " \n\t "));

        assertEquals("conversation message content must not be blank", exception.getMessage());
    }
}
