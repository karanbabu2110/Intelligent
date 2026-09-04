package io.kaos.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OllamaEmbeddingConfigurationTest {
    @Test
    void acceptsAValidatedExplicitEmbeddingModel() {
        assertEquals("embeddinggemma:latest",
                new OllamaEmbeddingConfiguration(" embeddinggemma:latest ").modelName());
    }

    @Test
    void rejectsMissingAndUnsafeModelNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new OllamaEmbeddingConfiguration(" "));
        assertThrows(IllegalArgumentException.class,
                () -> new OllamaEmbeddingConfiguration("https://remote.example/model"));
    }
}
