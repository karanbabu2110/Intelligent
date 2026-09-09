package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.tool.httpget.HttpGetResult;

/** Application port for the two bounded Ollama requests in one HTTP GET tool use. */
interface HttpGetPromptSubmission {
    OllamaPromptClient.Result request(OllamaModelConfiguration model, OllamaPrompt prompt);

    OllamaPromptClient.Result continueWithResult(
            OllamaModelConfiguration model,
            OllamaPrompt prompt,
            OllamaPromptClient.Result toolCallResult,
            HttpGetResult result);
}
