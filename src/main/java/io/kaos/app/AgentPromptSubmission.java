package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.tool.ToolResult;
import java.util.List;

/** Application port for one plan proposal and one final bounded synthesis. */
interface AgentPromptSubmission {
    OllamaPromptClient.Result propose(
            OllamaModelConfiguration model, OllamaPrompt planningPrompt);

    OllamaPromptClient.Result synthesize(OllamaModelConfiguration model,
            OllamaPrompt synthesisPrompt, List<ToolResult<?>> evidence);
}
