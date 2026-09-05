package io.kaos.app;

import io.kaos.conversation.ConversationHistory;
import io.kaos.knowledge.GroundedPrompt;
import java.util.Objects;

/** Retrieves local evidence and submits one exact grounded prompt for an answer. */
final class KnowledgeAskCommand {
    private final KnowledgeRetrieveCommand retrievalCommand;
    private final OllamaPromptCommand promptCommand;

    KnowledgeAskCommand(
            KnowledgeRetrieveCommand retrievalCommand,
            OllamaPromptCommand promptCommand) {
        this.retrievalCommand = Objects.requireNonNull(retrievalCommand, "retrievalCommand");
        this.promptCommand = Objects.requireNonNull(promptCommand, "promptCommand");
    }

    int execute(String queryText) {
        KnowledgeRetrieveCommand.PreparationOutcome preparation =
                retrievalCommand.prepare(queryText);
        if (!preparation.successful()) return preparation.exitCode();

        GroundedPrompt groundedPrompt = preparation.prompt();
        OllamaPromptCommand.PromptOutcome answer = promptCommand.submit(
                groundedPrompt.text(), ConversationHistory.empty());
        if (answer.exitCode() != KaosApplication.SUCCESS) return answer.exitCode();

        retrievalCommand.printCitations(groundedPrompt.citations());
        return KaosApplication.SUCCESS;
    }
}
