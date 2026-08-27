package io.kaos.ai.ollama;

/** One bounded prompt intentionally supplied for local Ollama generation. */
public record OllamaPrompt(String text) {
    public static final int MAX_PROMPT_CODE_POINTS = 4096;

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
    }

    private static boolean isUnsafeControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }
}
