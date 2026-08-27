package io.kaos.ai.ollama;

import java.util.regex.Pattern;

/** Selects the one local Ollama model used by the current KAOS application. */
public record OllamaModelConfiguration(String modelName) {
    public static final String MODEL_SYSTEM_PROPERTY = "kaos.ollama.model";
    public static final String MODEL_ENVIRONMENT_VARIABLE = "KAOS_OLLAMA_MODEL";
    public static final int MAX_MODEL_NAME_LENGTH = 128;

    private static final Pattern SAFE_MODEL_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]*"
                    + "(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*"
                    + "(?::[A-Za-z0-9][A-Za-z0-9._-]*)?");

    public OllamaModelConfiguration {
        modelName = validateModelName(modelName, "model name");
    }

    /**
     * Loads the explicit local model selection.
     *
     * <p>The system property has precedence over the environment variable. No
     * default is assumed because KAOS must not silently select or download a
     * model.</p>
     *
     * @return validated Ollama model configuration
     * @throws IllegalStateException if process configuration cannot be read
     * @throws IllegalArgumentException if the selection is absent or invalid
     */
    public static OllamaModelConfiguration load() {
        try {
            return resolve(
                    System.getProperty(MODEL_SYSTEM_PROPERTY),
                    System.getenv(MODEL_ENVIRONMENT_VARIABLE));
        } catch (SecurityException exception) {
            throw new IllegalStateException(
                    "Unable to read local Ollama model configuration.", exception);
        }
    }

    static OllamaModelConfiguration resolve(
            String systemPropertyValue, String environmentValue) {
        if (systemPropertyValue != null) {
            return new OllamaModelConfiguration(validateModelName(
                    systemPropertyValue,
                    "system property '" + MODEL_SYSTEM_PROPERTY + "'"));
        }
        if (environmentValue != null) {
            return new OllamaModelConfiguration(validateModelName(
                    environmentValue,
                    "environment variable '" + MODEL_ENVIRONMENT_VARIABLE + "'"));
        }
        throw new IllegalArgumentException(
                "An explicit local Ollama model must be configured.");
    }

    private static String validateModelName(String value, String source) {
        if (value == null) {
            throw new IllegalArgumentException(source + " must not be null.");
        }

        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(source + " must not be blank.");
        }
        if (normalized.length() > MAX_MODEL_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    source + " must contain at most " + MAX_MODEL_NAME_LENGTH + " characters.");
        }
        if (!SAFE_MODEL_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    source + " is not a supported Ollama model name.");
        }
        return normalized;
    }
}
