package io.kaos.ai.ollama;

import java.util.Objects;
import java.util.regex.Pattern;

/** Selects the local Ollama model, context, thinking mode, and response limit. */
public record OllamaModelConfiguration(
        String modelName,
        int contextWindow,
        OllamaThinkingMode thinkingMode,
        int responseTokenLimit) {
    public static final String MODEL_SYSTEM_PROPERTY = "kaos.ollama.model";
    public static final String MODEL_ENVIRONMENT_VARIABLE = "KAOS_OLLAMA_MODEL";
    public static final String CONTEXT_WINDOW_SYSTEM_PROPERTY = "kaos.ollama.context-window";
    public static final String CONTEXT_WINDOW_ENVIRONMENT_VARIABLE =
            "KAOS_OLLAMA_CONTEXT_WINDOW";
    public static final String THINKING_SYSTEM_PROPERTY = "kaos.ollama.thinking";
    public static final String THINKING_ENVIRONMENT_VARIABLE = "KAOS_OLLAMA_THINKING";
    public static final String RESPONSE_TOKEN_LIMIT_SYSTEM_PROPERTY =
            "kaos.ollama.response-token-limit";
    public static final String RESPONSE_TOKEN_LIMIT_ENVIRONMENT_VARIABLE =
            "KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT";
    public static final int MAX_MODEL_NAME_LENGTH = 128;
    public static final int DEFAULT_CONTEXT_WINDOW = 4_096;
    public static final int MIN_CONTEXT_WINDOW = 2_048;
    public static final int MAX_CONTEXT_WINDOW = 65_536;
    public static final int DEFAULT_ORDINARY_RESPONSE_TOKEN_LIMIT = 512;
    public static final int DEFAULT_REASONING_RESPONSE_TOKEN_LIMIT = 2_048;
    public static final int MIN_RESPONSE_TOKEN_LIMIT = 64;
    public static final int MAX_RESPONSE_TOKEN_LIMIT = 4_096;

    private static final Pattern SAFE_MODEL_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]*"
                    + "(?:/[A-Za-z0-9][A-Za-z0-9._-]*)*"
                    + "(?::[A-Za-z0-9][A-Za-z0-9._-]*)?");

    public OllamaModelConfiguration {
        modelName = validateModelName(modelName, "model name");
        contextWindow = validateContextWindow(contextWindow, "context window");
        thinkingMode = Objects.requireNonNull(thinkingMode, "thinkingMode");
        responseTokenLimit = validateResponseTokenLimit(
                responseTokenLimit, "response token limit");
    }

    /** Uses the evidence-selected response limit for the explicit thinking mode. */
    public OllamaModelConfiguration(
            String modelName, int contextWindow, OllamaThinkingMode thinkingMode) {
        this(
                modelName,
                contextWindow,
                thinkingMode,
                defaultResponseTokenLimit(thinkingMode));
    }

    /** Uses the ordinary-request thinking default. */
    public OllamaModelConfiguration(String modelName, int contextWindow) {
        this(modelName, contextWindow, OllamaThinkingMode.OFF);
    }

    /** Uses the evidence-selected ordinary-request context and thinking defaults. */
    public OllamaModelConfiguration(String modelName) {
        this(modelName, DEFAULT_CONTEXT_WINDOW, OllamaThinkingMode.OFF);
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
                    System.getenv(MODEL_ENVIRONMENT_VARIABLE),
                    System.getProperty(CONTEXT_WINDOW_SYSTEM_PROPERTY),
                    System.getenv(CONTEXT_WINDOW_ENVIRONMENT_VARIABLE),
                    System.getProperty(THINKING_SYSTEM_PROPERTY),
                    System.getenv(THINKING_ENVIRONMENT_VARIABLE),
                    System.getProperty(RESPONSE_TOKEN_LIMIT_SYSTEM_PROPERTY),
                    System.getenv(RESPONSE_TOKEN_LIMIT_ENVIRONMENT_VARIABLE));
        } catch (SecurityException exception) {
            throw new IllegalStateException(
                    "Unable to read local Ollama model configuration.", exception);
        }
    }

    static OllamaModelConfiguration resolve(
            String modelSystemProperty,
            String modelEnvironmentValue,
            String contextSystemProperty,
            String contextEnvironmentValue) {
        return resolve(
                modelSystemProperty,
                modelEnvironmentValue,
                contextSystemProperty,
                contextEnvironmentValue,
                null,
                null,
                null,
                null);
    }

    static OllamaModelConfiguration resolve(
            String modelSystemProperty,
            String modelEnvironmentValue,
            String contextSystemProperty,
            String contextEnvironmentValue,
            String thinkingSystemProperty,
            String thinkingEnvironmentValue) {
        return resolve(
                modelSystemProperty,
                modelEnvironmentValue,
                contextSystemProperty,
                contextEnvironmentValue,
                thinkingSystemProperty,
                thinkingEnvironmentValue,
                null,
                null);
    }

    static OllamaModelConfiguration resolve(
            String modelSystemProperty,
            String modelEnvironmentValue,
            String contextSystemProperty,
            String contextEnvironmentValue,
            String thinkingSystemProperty,
            String thinkingEnvironmentValue,
            String responseTokenLimitSystemProperty,
            String responseTokenLimitEnvironmentValue) {
        String modelName;
        if (modelSystemProperty != null) {
            modelName = validateModelName(
                    modelSystemProperty,
                    "system property '" + MODEL_SYSTEM_PROPERTY + "'");
        } else if (modelEnvironmentValue != null) {
            modelName = validateModelName(
                    modelEnvironmentValue,
                    "environment variable '" + MODEL_ENVIRONMENT_VARIABLE + "'");
        } else {
            throw new IllegalArgumentException(
                    "An explicit local Ollama model must be configured.");
        }

        int contextWindow = DEFAULT_CONTEXT_WINDOW;
        if (contextSystemProperty != null) {
            contextWindow = validateContextWindow(
                    contextSystemProperty,
                    "system property '" + CONTEXT_WINDOW_SYSTEM_PROPERTY + "'");
        } else if (contextEnvironmentValue != null) {
            contextWindow = validateContextWindow(
                    contextEnvironmentValue,
                    "environment variable '" + CONTEXT_WINDOW_ENVIRONMENT_VARIABLE + "'");
        }
        OllamaThinkingMode thinkingMode = OllamaThinkingMode.OFF;
        if (thinkingSystemProperty != null) {
            thinkingMode = OllamaThinkingMode.parse(
                    thinkingSystemProperty,
                    "system property '" + THINKING_SYSTEM_PROPERTY + "'");
        } else if (thinkingEnvironmentValue != null) {
            thinkingMode = OllamaThinkingMode.parse(
                    thinkingEnvironmentValue,
                    "environment variable '" + THINKING_ENVIRONMENT_VARIABLE + "'");
        }
        int responseTokenLimit = defaultResponseTokenLimit(thinkingMode);
        if (responseTokenLimitSystemProperty != null) {
            responseTokenLimit = validateResponseTokenLimit(
                    responseTokenLimitSystemProperty,
                    "system property '" + RESPONSE_TOKEN_LIMIT_SYSTEM_PROPERTY + "'");
        } else if (responseTokenLimitEnvironmentValue != null) {
            responseTokenLimit = validateResponseTokenLimit(
                    responseTokenLimitEnvironmentValue,
                    "environment variable '" + RESPONSE_TOKEN_LIMIT_ENVIRONMENT_VARIABLE + "'");
        }
        return new OllamaModelConfiguration(
                modelName, contextWindow, thinkingMode, responseTokenLimit);
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

    private static int validateContextWindow(String value, String source) {
        if (value == null) {
            throw new IllegalArgumentException(source + " must not be null.");
        }

        String normalized = value.strip();
        if (normalized.isEmpty()
                || !normalized.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw new IllegalArgumentException(source + " must be a whole number of tokens.");
        }
        try {
            return validateContextWindow(Integer.parseInt(normalized), source);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(source + " is outside the supported range.");
        }
    }

    private static int validateContextWindow(int value, String source) {
        if (value < MIN_CONTEXT_WINDOW || value > MAX_CONTEXT_WINDOW) {
            throw new IllegalArgumentException(
                    source + " must be between " + MIN_CONTEXT_WINDOW + " and "
                            + MAX_CONTEXT_WINDOW + " tokens.");
        }
        return value;
    }

    private static int defaultResponseTokenLimit(OllamaThinkingMode thinkingMode) {
        return Objects.requireNonNull(thinkingMode, "thinkingMode") == OllamaThinkingMode.ON
                ? DEFAULT_REASONING_RESPONSE_TOKEN_LIMIT
                : DEFAULT_ORDINARY_RESPONSE_TOKEN_LIMIT;
    }

    private static int validateResponseTokenLimit(String value, String source) {
        if (value == null) {
            throw new IllegalArgumentException(source + " must not be null.");
        }

        String normalized = value.strip();
        if (normalized.isEmpty()
                || !normalized.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw new IllegalArgumentException(source + " must be a whole number of tokens.");
        }
        try {
            return validateResponseTokenLimit(Integer.parseInt(normalized), source);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(source + " is outside the supported range.");
        }
    }

    private static int validateResponseTokenLimit(int value, String source) {
        if (value < MIN_RESPONSE_TOKEN_LIMIT || value > MAX_RESPONSE_TOKEN_LIMIT) {
            throw new IllegalArgumentException(
                    source + " must be between " + MIN_RESPONSE_TOKEN_LIMIT + " and "
                            + MAX_RESPONSE_TOKEN_LIMIT + " tokens.");
        }
        return value;
    }
}
