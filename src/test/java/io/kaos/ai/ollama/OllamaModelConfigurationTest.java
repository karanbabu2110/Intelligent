package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OllamaModelConfigurationTest {
    @Test
    void requiresAnExplicitModelSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(null, null));

        assertTrue(exception.getMessage().contains("explicit"));
    }

    @Test
    void usesTheEnvironmentWhenTheSystemPropertyIsAbsent() {
        OllamaModelConfiguration configuration =
                OllamaModelConfiguration.resolve(null, "llama3.2:latest");

        assertEquals("llama3.2:latest", configuration.modelName());
    }

    @Test
    void givesTheSystemPropertyPrecedence() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "qwen3:8b", "llama3.2:latest");

        assertEquals("qwen3:8b", configuration.modelName());
    }

    @Test
    void trimsOuterWhitespace() {
        OllamaModelConfiguration configuration =
                OllamaModelConfiguration.resolve("  gemma3:4b  ", null);

        assertEquals("gemma3:4b", configuration.modelName());
    }

    @Test
    void acceptsANamespacedModel() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "hf.co/team/model-name:Q4_K_M", null);

        assertEquals("hf.co/team/model-name:Q4_K_M", configuration.modelName());
    }

    @Test
    void rejectsABlankConfiguredValueInsteadOfFallingBack() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve("   ", "llama3.2"));

        assertTrue(exception.getMessage().contains(
                OllamaModelConfiguration.MODEL_SYSTEM_PROPERTY));
    }

    @Test
    void rejectsUnsafeOrUnsupportedCharacters() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve("model\nprivate-value", null));

        assertTrue(exception.getMessage().contains("not a supported"));
    }

    @Test
    void rejectsEmptyNamespaceSegments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve("team//model:latest", null));
    }

    @Test
    void rejectsAnOversizedModelName() {
        String oversized = "m".repeat(OllamaModelConfiguration.MAX_MODEL_NAME_LENGTH + 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(null, oversized));

        assertTrue(exception.getMessage().contains("at most 128"));
    }
}
