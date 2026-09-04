package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationDatabasePath;
import io.kaos.knowledge.KnowledgeDatabasePath;
import io.kaos.knowledge.KnowledgeStorageException;
import io.kaos.knowledge.SqliteKnowledgeStore;
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
    private final Supplier<OllamaEmbeddingConfiguration> embeddingConfigurationLoader;
    private final EmbeddingSubmission embeddingSubmission;
    private final KnowledgeStorageSubmission knowledgeStorageSubmission;

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader) {
        this(connectivityCheck, modelConfigurationLoader, promptSubmission,
                conversationDatabasePathLoader, OllamaEmbeddingConfiguration::load,
                (configuration, chunks) ->
                        new OllamaEmbeddingClient().embed(configuration, chunks),
                ApplicationRuntime::storeKnowledge);
    }

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            Supplier<OllamaEmbeddingConfiguration> embeddingConfigurationLoader,
            EmbeddingSubmission embeddingSubmission) {
        this(connectivityCheck, modelConfigurationLoader, promptSubmission,
                conversationDatabasePathLoader, embeddingConfigurationLoader,
                embeddingSubmission, ApplicationRuntime::storeKnowledge);
    }

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            Supplier<OllamaEmbeddingConfiguration> embeddingConfigurationLoader,
            EmbeddingSubmission embeddingSubmission,
            KnowledgeStorageSubmission knowledgeStorageSubmission) {
        this.connectivityCheck = Objects.requireNonNull(connectivityCheck, "connectivityCheck");
        this.modelConfigurationLoader = Objects.requireNonNull(
                modelConfigurationLoader, "modelConfigurationLoader");
        this.promptSubmission = Objects.requireNonNull(promptSubmission, "promptSubmission");
        this.conversationDatabasePathLoader = Objects.requireNonNull(
                conversationDatabasePathLoader, "conversationDatabasePathLoader");
        this.embeddingConfigurationLoader = Objects.requireNonNull(
                embeddingConfigurationLoader, "embeddingConfigurationLoader");
        this.embeddingSubmission = Objects.requireNonNull(
                embeddingSubmission, "embeddingSubmission");
        this.knowledgeStorageSubmission = Objects.requireNonNull(
                knowledgeStorageSubmission, "knowledgeStorageSubmission");
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
                new KnowledgeIngestCommand(
                        context, embeddingConfigurationLoader, embeddingSubmission,
                        knowledgeStorageSubmission)::execute,
                new KnowledgeRetrieveCommand(context)::execute,
                ollamaStatusCommand::execute,
                ollamaModelCommand::execute,
                ollamaPromptCommand::execute,
                conversationCommand::execute)
                .route(arguments);
    }

    private static long storeKnowledge(
            String model, java.util.List<io.kaos.knowledge.EmbeddedChunk> chunks) {
        try {
            return new SqliteKnowledgeStore(KnowledgeDatabasePath.load()).store(model, chunks);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw KnowledgeStorageException.unavailable();
        }
    }
}
