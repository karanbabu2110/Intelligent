package io.kaos.ai.ollama;

/** One bounded prompt and optional KAOS-controlled instruction for local Ollama. */
public record OllamaPrompt(String text, String systemInstruction) {
    public static final int MAX_PROMPT_CODE_POINTS = 4096;
    public static final int MAX_SYSTEM_INSTRUCTION_CODE_POINTS = 256;

    public OllamaPrompt(String text) {
        this(text, "");
    }

    public OllamaPrompt {
        if (text == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }

        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (text.codePointCount(0, text.length()) > MAX_PROMPT_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "prompt must contain at most " + MAX_PROMPT_CODE_POINTS + " characters");
        }
        if (text.codePoints().anyMatch(OllamaPrompt::isUnsafeControlCharacter)) {
            throw new IllegalArgumentException("prompt contains an unsupported control character");
        }
        if (systemInstruction == null) {
            throw new IllegalArgumentException("system instruction must not be null");
        }
        systemInstruction = systemInstruction.strip();
        if (systemInstruction.codePointCount(0, systemInstruction.length())
                > MAX_SYSTEM_INSTRUCTION_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "system instruction must contain at most "
                            + MAX_SYSTEM_INSTRUCTION_CODE_POINTS + " characters");
        }
        if (systemInstruction.codePoints().anyMatch(
                OllamaPrompt::isUnsafeControlCharacter)) {
            throw new IllegalArgumentException(
                    "system instruction contains an unsupported control character");
        }
    }

    private static boolean isUnsafeControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }
}
