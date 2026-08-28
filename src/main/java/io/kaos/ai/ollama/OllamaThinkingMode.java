package io.kaos.ai.ollama;

import java.util.Locale;

/** Explicit thinking policy for one local Ollama prompt request. */
public enum OllamaThinkingMode {
    OFF(false, "off"),
    ON(true, "on");

    private final boolean enabled;
    private final String configurationValue;

    OllamaThinkingMode(boolean enabled, String configurationValue) {
        this.enabled = enabled;
        this.configurationValue = configurationValue;
    }

    /** Value sent to Ollama's boolean {@code think} request field. */
    public boolean enabled() {
        return enabled;
    }

    /** Stable value accepted from and displayed in process configuration. */
    public String configurationValue() {
        return configurationValue;
    }

    static OllamaThinkingMode parse(String value, String source) {
        if (value == null) {
            throw new IllegalArgumentException(source + " must not be null.");
        }

        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "off" -> OFF;
            case "on" -> ON;
            default -> throw new IllegalArgumentException(source + " must be 'off' or 'on'.");
        };
    }
}
