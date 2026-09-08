package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationDatabasePath;
import io.kaos.knowledge.KnowledgeDatabasePath;
import io.kaos.knowledge.KnowledgeStorageException;
import io.kaos.knowledge.SqliteKnowledgeStore;
import io.kaos.memory.AnswerDetail;
import io.kaos.memory.MemoryDatabasePath;
import io.kaos.memory.SqliteAnswerDetailStore;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
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
    private final KnowledgeDocumentLoader knowledgeDocumentLoader;
    private final Supplier<Optional<AnswerDetail>> answerDetailLoader;

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader) {
        this(connectivityCheck, modelConfigurationLoader, promptSubmission,
                conversationDatabasePathLoader, OllamaEmbeddingConfiguration::load,
                (configuration, chunks) ->
                        new OllamaEmbeddingClient().embed(configuration, chunks),
                ApplicationRuntime::storeKnowledge, ApplicationRuntime::loadKnowledge);
    }

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            Supplier<Optional<AnswerDetail>> answerDetailLoader) {
        this(connectivityCheck, modelConfigurationLoader, promptSubmission,
                conversationDatabasePathLoader, OllamaEmbeddingConfiguration::load,
                (configuration, chunks) ->
                        new OllamaEmbeddingClient().embed(configuration, chunks),
                ApplicationRuntime::storeKnowledge, ApplicationRuntime::loadKnowledge,
                answerDetailLoader);
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
                embeddingSubmission, ApplicationRuntime::storeKnowledge,
                ApplicationRuntime::loadKnowledge);
    }

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            Supplier<OllamaEmbeddingConfiguration> embeddingConfigurationLoader,
            EmbeddingSubmission embeddingSubmission,
            KnowledgeStorageSubmission knowledgeStorageSubmission) {
        this(connectivityCheck, modelConfigurationLoader, promptSubmission,
                conversationDatabasePathLoader, embeddingConfigurationLoader,
                embeddingSubmission, knowledgeStorageSubmission,
                ApplicationRuntime::loadKnowledge);
    }

    ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            Supplier<OllamaEmbeddingConfiguration> embeddingConfigurationLoader,
            EmbeddingSubmission embeddingSubmission,
            KnowledgeStorageSubmission knowledgeStorageSubmission,
            KnowledgeDocumentLoader knowledgeDocumentLoader) {
        this(connectivityCheck, modelConfigurationLoader, promptSubmission,
                conversationDatabasePathLoader, embeddingConfigurationLoader,
                embeddingSubmission, knowledgeStorageSubmission, knowledgeDocumentLoader,
                Optional::empty);
    }

    private ApplicationRuntime(
            Supplier<OllamaConnectivity.Result> connectivityCheck,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            Supplier<OllamaEmbeddingConfiguration> embeddingConfigurationLoader,
            EmbeddingSubmission embeddingSubmission,
            KnowledgeStorageSubmission knowledgeStorageSubmission,
            KnowledgeDocumentLoader knowledgeDocumentLoader,
            Supplier<Optional<AnswerDetail>> answerDetailLoader) {
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
        this.knowledgeDocumentLoader = Objects.requireNonNull(
                knowledgeDocumentLoader, "knowledgeDocumentLoader");
        this.answerDetailLoader = Objects.requireNonNull(
                answerDetailLoader, "answerDetailLoader");
    }

    static ApplicationRuntime local() {
        return new ApplicationRuntime(
                () -> new OllamaConnectivity().check(),
                OllamaModelConfiguration::load,
                (model, history, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(
                                model, history, prompt, thinking, chunks),
                ConversationDatabasePath::load,
                OllamaEmbeddingConfiguration::load,
                (configuration, chunks) ->
                        new OllamaEmbeddingClient().embed(configuration, chunks),
                ApplicationRuntime::storeKnowledge, ApplicationRuntime::loadKnowledge,
                ApplicationRuntime::loadAnswerDetail);
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
                context, modelConfigurationLoader, promptSubmission, answerDetailLoader);
        ConversationCommand conversationCommand = new ConversationCommand(
                context, ollamaPromptCommand, conversationDatabasePathLoader);
        KnowledgeRetrieveCommand knowledgeRetrieveCommand = new KnowledgeRetrieveCommand(
                context, embeddingConfigurationLoader, embeddingSubmission,
                knowledgeDocumentLoader);
        KnowledgeAskCommand knowledgeAskCommand = new KnowledgeAskCommand(
                knowledgeRetrieveCommand, ollamaPromptCommand);
        MemoryCreateCommand memoryCreateCommand = new MemoryCreateCommand(
                context, () -> new SqliteAnswerDetailStore(MemoryDatabasePath.load()));
        MemoryInspectCommand memoryInspectCommand = new MemoryInspectCommand(
                context, answerDetailLoader);
        java.util.function.Supplier<io.kaos.memory.AnswerDetailStore> memoryStore =
                () -> new SqliteAnswerDetailStore(MemoryDatabasePath.load());
        MemoryEditCommand memoryEditCommand = new MemoryEditCommand(context, memoryStore);
        MemoryDeleteCommand memoryDeleteCommand = new MemoryDeleteCommand(context, memoryStore);
        MemoryPrivacyCommand memoryPrivacyCommand = new MemoryPrivacyCommand(
                context, answerDetailLoader);
        ReadLocalFileCommand readLocalFileCommand = new ReadLocalFileCommand(
                context,
                modelConfigurationLoader,
                new ReadLocalFilePromptSubmission() {
                    private OllamaPromptClient client;

                    @Override
                    public OllamaPromptClient.Result request(
                            OllamaModelConfiguration model,
                            OllamaPrompt prompt) {
                        return client().submitWithReadLocalFileTool(model, prompt);
                    }

                    @Override
                    public OllamaPromptClient.Result continueWithResult(
                            OllamaModelConfiguration model,
                            OllamaPrompt prompt,
                            OllamaPromptClient.Result toolCallResult,
                            ReadLocalFileResult result) {
                        return client().continueWithReadLocalFileResult(
                                model, prompt, toolCallResult, result);
                    }

                    private OllamaPromptClient client() {
                        if (client == null) {
                            client = new OllamaPromptClient();
                        }
                        return client;
                    }
                },
                ReadLocalFilePermissionValidator::load);
        return new CommandRouter(
                context,
                new KnowledgeIngestCommand(
                        context, embeddingConfigurationLoader, embeddingSubmission,
                        knowledgeStorageSubmission)::execute,
                knowledgeRetrieveCommand::execute,
                knowledgeAskCommand::execute,
                memoryCreateCommand::execute,
                memoryInspectCommand::execute,
                memoryEditCommand::execute,
                memoryDeleteCommand::execute,
                memoryPrivacyCommand::execute,
                readLocalFileCommand::execute,
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

    private static java.util.List<io.kaos.knowledge.StoredKnowledgeDocument> loadKnowledge() {
        try {
            return new SqliteKnowledgeStore(KnowledgeDatabasePath.load()).loadAll();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw KnowledgeStorageException.unavailable();
        }
    }

    private static Optional<AnswerDetail> loadAnswerDetail() {
        return new SqliteAnswerDetailStore(MemoryDatabasePath.load()).retrieve();
    }
}
