package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OllamaPromptTest {
    @Test
    void trimsOuterWhitespaceWithoutChangingInnerContent() {
        OllamaPrompt prompt = new OllamaPrompt("  explain local AI  ");

        assertEquals("explain local AI", prompt.text());
        assertEquals("", prompt.systemInstruction());
    }

    @Test
    void permitsNewlinesAndTabsThatJsonWillEscape() {
        OllamaPrompt prompt = new OllamaPrompt("first line\n\tsecond line");

        assertEquals("first line\n\tsecond line", prompt.text());
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> new OllamaPrompt("   "));
    }

    @Test
    void rejectsAnOversizedPrompt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaPrompt("p".repeat(OllamaPrompt.MAX_PROMPT_CODE_POINTS + 1)));
    }

    @Test
    void rejectsTerminalControlCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaPrompt("safe\u001b[31mprivate"));
    }

    @Test
    void validatesAndTrimsAnOptionalSystemInstructionSeparately() {
        OllamaPrompt prompt = new OllamaPrompt(
                "explain local AI", "  Answer concisely.  ");

        assertEquals("explain local AI", prompt.text());
        assertEquals("Answer concisely.", prompt.systemInstruction());
        assertThrows(IllegalArgumentException.class,
                () -> new OllamaPrompt("prompt", (String) null));
        assertThrows(IllegalArgumentException.class,
                () -> new OllamaPrompt("prompt", "x".repeat(
                        OllamaPrompt.MAX_SYSTEM_INSTRUCTION_CODE_POINTS + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new OllamaPrompt("prompt", "safe\u001b[31mprivate"));
    }
}
