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
                () -> OllamaModelConfiguration.resolve(null, null, null, null));

        assertTrue(exception.getMessage().contains("explicit"));
    }

    @Test
    void usesTheEnvironmentWhenTheSystemPropertyIsAbsent() {
        OllamaModelConfiguration configuration =
                OllamaModelConfiguration.resolve(null, "llama3.2:latest", null, null);

        assertEquals("llama3.2:latest", configuration.modelName());
    }

    @Test
    void givesTheSystemPropertyPrecedence() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "qwen3:8b", "llama3.2:latest", null, null);

        assertEquals("qwen3:8b", configuration.modelName());
    }

    @Test
    void trimsOuterWhitespace() {
        OllamaModelConfiguration configuration =
                OllamaModelConfiguration.resolve("  gemma3:4b  ", null, null, null);

        assertEquals("gemma3:4b", configuration.modelName());
    }

    @Test
    void acceptsANamespacedModel() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "hf.co/team/model-name:Q4_K_M", null, null, null);

        assertEquals("hf.co/team/model-name:Q4_K_M", configuration.modelName());
    }

    @Test
    void rejectsABlankConfiguredValueInsteadOfFallingBack() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve("   ", "llama3.2", null, null));

        assertTrue(exception.getMessage().contains(
                OllamaModelConfiguration.MODEL_SYSTEM_PROPERTY));
    }

    @Test
    void rejectsUnsafeOrUnsupportedCharacters() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(
                        "model\nprivate-value", null, null, null));

        assertTrue(exception.getMessage().contains("not a supported"));
    }

    @Test
    void rejectsEmptyNamespaceSegments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(
                        "team//model:latest", null, null, null));
    }

    @Test
    void rejectsAnOversizedModelName() {
        String oversized = "m".repeat(OllamaModelConfiguration.MAX_MODEL_NAME_LENGTH + 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(null, oversized, null, null));

        assertTrue(exception.getMessage().contains("at most 128"));
    }

    @Test
    void usesTheEvidenceSelectedContextDefault() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "qwen3:8b", null, null, null);

        assertEquals(4_096, configuration.contextWindow());
    }

    @Test
    void usesTheContextEnvironmentSetting() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "qwen3:8b", null, null, "8192");

        assertEquals(8_192, configuration.contextWindow());
    }

    @Test
    void givesTheContextSystemPropertyPrecedence() {
        OllamaModelConfiguration configuration = OllamaModelConfiguration.resolve(
                "qwen3:8b", null, "2048", "8192");

        assertEquals(2_048, configuration.contextWindow());
    }

    @Test
    void acceptsTheLargestSupportedContextWindow() {
        OllamaModelConfiguration configuration =
                new OllamaModelConfiguration("qwen3:8b", 65_536);

        assertEquals(65_536, configuration.contextWindow());
    }

    @Test
    void rejectsABlankContextPropertyInsteadOfFallingBack() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(
                        "qwen3:8b", null, "  ", "8192"));

        assertTrue(exception.getMessage().contains(
                OllamaModelConfiguration.CONTEXT_WINDOW_SYSTEM_PROPERTY));
    }

    @Test
    void rejectsNonNumericOrOutOfRangeContextWindows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OllamaModelConfiguration.resolve(
                        "qwen3:8b", null, "private-value", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaModelConfiguration("qwen3:8b", 1_024));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaModelConfiguration("qwen3:8b", 65_537));
    }
}
