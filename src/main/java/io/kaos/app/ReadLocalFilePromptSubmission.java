package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;

/** Application port for the two bounded Ollama requests in one local-file tool use. */
interface ReadLocalFilePromptSubmission {
    OllamaPromptClient.Result request(
            OllamaModelConfiguration model, OllamaPrompt prompt);

    OllamaPromptClient.Result continueWithResult(
            OllamaModelConfiguration model,
            OllamaPrompt prompt,
            OllamaPromptClient.Result toolCallResult,
            ReadLocalFileResult result);
}
