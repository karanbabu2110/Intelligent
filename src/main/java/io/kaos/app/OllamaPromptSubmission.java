package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.conversation.ConversationHistory;
import java.util.function.Consumer;

/** Application port for submitting one streaming prompt to Ollama. */
@FunctionalInterface
interface OllamaPromptSubmission {
    OllamaPromptClient.Result submit(
            OllamaModelConfiguration model,
            ConversationHistory history,
            OllamaPrompt prompt,
            Runnable thinkingStarted,
            Consumer<String> answerChunkConsumer);
}
