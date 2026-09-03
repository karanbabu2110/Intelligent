package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationDatabasePath;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/** Composes application commands from explicit process-level dependencies. */
final class ApplicationRuntime {
    private final Supplier<OllamaConnectivity.Result> connectivityCheck;
    private final Supplier<OllamaModelConfiguration> modelConfigurationLoader;
    private final OllamaPromptSubmission promptSubmission;
    private final Supplier<Path> conversationDatabasePathLoader;

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader) {
        this.connectivityCheck = Objects.requireNonNull(connectivityCheck, "connectivityCheck");
        this.modelConfigurationLoader = Objects.requireNonNull(
                modelConfigurationLoader, "modelConfigurationLoader");
        this.promptSubmission = Objects.requireNonNull(promptSubmission, "promptSubmission");
        this.conversationDatabasePathLoader = Objects.requireNonNull(
                conversationDatabasePathLoader, "conversationDatabasePathLoader");
    }

    static ApplicationRuntime local() {
        return new ApplicationRuntime(
                () -> new OllamaConnectivity().check(),
                OllamaModelConfiguration::load,
                (model, history, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(
                                model, history, prompt, thinking, chunks),
                ConversationDatabasePath::load);
    }

    int execute(
            String[] arguments,
            ApplicationConfiguration configuration,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        CommandContext context = new CommandContext(configuration, input, output, errorOutput);
        OllamaStatusCommand ollamaStatusCommand = new OllamaStatusCommand(
                context, connectivityCheck);
        OllamaModelCommand ollamaModelCommand = new OllamaModelCommand(
                context, modelConfigurationLoader);
        OllamaPromptCommand ollamaPromptCommand = new OllamaPromptCommand(
                context, modelConfigurationLoader, promptSubmission);
        ConversationCommand conversationCommand = new ConversationCommand(
                context, ollamaPromptCommand, conversationDatabasePathLoader);
        return new CommandRouter(
                context,
                new KnowledgeIngestCommand(context)::execute,
                ollamaStatusCommand::execute,
                ollamaModelCommand::execute,
                ollamaPromptCommand::execute,
                conversationCommand::execute)
                .route(arguments);
    }
}
