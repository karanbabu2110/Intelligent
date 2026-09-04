package io.kaos.ai.ollama;

import java.util.regex.Pattern;

/** Selects the explicit local Ollama model used only for embeddings. */
public record OllamaEmbeddingConfiguration(String modelName) {
    public static final String MODEL_SYSTEM_PROPERTY = "kaos.ollama.embedding-model";
    public static final String MODEL_ENVIRONMENT_VARIABLE = "KAOS_OLLAMA_EMBEDDING_MODEL";
    public static final int MAX_MODEL_NAME_LENGTH = 128;
    private static final Pattern SAFE_MODEL_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]*(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*"
                    + "(?::[A-Za-z0-9][A-Za-z0-9._-]*)?");

    public OllamaEmbeddingConfiguration {
        modelName = validate(modelName, "embedding model name");
    }

    /** Loads an explicit selection without assuming or downloading a model. */
    public static OllamaEmbeddingConfiguration load() {
        try {
            String property = System.getProperty(MODEL_SYSTEM_PROPERTY);
            String environment = System.getenv(MODEL_ENVIRONMENT_VARIABLE);
            if (property != null) return new OllamaEmbeddingConfiguration(property);
            if (environment != null) return new OllamaEmbeddingConfiguration(environment);
            throw new IllegalArgumentException(
                    "An explicit local Ollama embedding model must be configured.");
        } catch (SecurityException exception) {
            throw new IllegalStateException(
                    "Unable to read local Ollama embedding model configuration.", exception);
        }
    }

    private static String validate(String value, String source) {
        if (value == null) throw new IllegalArgumentException(source + " must not be null.");
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(source + " must not be blank.");
        if (normalized.length() > MAX_MODEL_NAME_LENGTH
                || !SAFE_MODEL_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(source + " is not a supported Ollama model name.");
        }
        return normalized;
    }
}
