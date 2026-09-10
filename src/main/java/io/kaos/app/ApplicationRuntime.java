package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaEmbeddingClient;
import io.kaos.ai.ollama.OllamaEmbeddingConfiguration;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationDatabasePath;
import io.kaos.knowledge.EmbeddedChunk;
import io.kaos.knowledge.KnowledgeDatabasePath;
import io.kaos.knowledge.KnowledgeStorageException;
import io.kaos.knowledge.SqliteKnowledgeStore;
import io.kaos.knowledge.StoredKnowledgeDocument;
import io.kaos.memory.AnswerDetail;
import io.kaos.memory.AnswerDetailStore;
import io.kaos.memory.MemoryDatabasePath;
import io.kaos.memory.SqliteAnswerDetailStore;
import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.httpget.HttpGetExecutor;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetResult;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
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
    private Supplier<OllamaPromptClient> localToolsClient;

    ApplicationRuntime withLocalToolsClient(Supplier<OllamaPromptClient> loader) {
        this.localToolsClient = Objects.requireNonNull(loader);
        return this;
    }

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
                ApplicationRuntime::loadAnswerDetail).withLocalToolsClient(() -> new OllamaPromptClient());
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
        Supplier<ToolRegistry> toolRegistry = StandardTools::create;
        LocalToolsCommand localToolsCommand = new LocalToolsCommand(context,
                modelConfigurationLoader, localToolsClient != null ? localToolsClient : () -> new OllamaPromptClient(toolRegistry.get()),
                toolRegistry);
        ToolCatalogCommand toolCatalogCommand = new ToolCatalogCommand(context, toolRegistry);
        if (localToolsClient != null) conversationCommand.withLocalTools(localToolsCommand);
        KnowledgeRetrieveCommand knowledgeRetrieveCommand = new KnowledgeRetrieveCommand(
                context, embeddingConfigurationLoader, embeddingSubmission,
                knowledgeDocumentLoader);
        KnowledgeAskCommand knowledgeAskCommand = new KnowledgeAskCommand(
                knowledgeRetrieveCommand, ollamaPromptCommand);
        MemoryCreateCommand memoryCreateCommand = new MemoryCreateCommand(
                context, () -> new SqliteAnswerDetailStore(MemoryDatabasePath.load()));
        MemoryInspectCommand memoryInspectCommand = new MemoryInspectCommand(
                context, answerDetailLoader);
        java.util.function.Supplier<AnswerDetailStore> memoryStore =
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
                            client = new OllamaPromptClient(toolRegistry.get());
                        }
                        return client;
                    }
                },
                ReadLocalFilePermissionValidator::load);
        HttpGetCommand httpGetCommand = new HttpGetCommand(
                context,
                modelConfigurationLoader,
                new HttpGetPromptSubmission() {
                    private OllamaPromptClient client;

                    @Override
                    public OllamaPromptClient.Result request(
                            OllamaModelConfiguration model, OllamaPrompt prompt) {
                        return client().submitWithHttpGetTool(model, prompt);
                    }

                    @Override
                    public OllamaPromptClient.Result continueWithResult(
                            OllamaModelConfiguration model,
                            OllamaPrompt prompt,
                            OllamaPromptClient.Result toolCallResult,
                            HttpGetResult result) {
                        return client().continueWithHttpGetResult(
                                model, prompt, toolCallResult, result);
                    }

                    private OllamaPromptClient client() {
                        if (client == null) client = new OllamaPromptClient(toolRegistry.get());
                        return client;
                    }
                },
                HttpGetPermissionValidator::load,
                (validator, grant) -> new HttpGetExecutor(validator).execute(grant));
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
                httpGetCommand::execute,
                ollamaStatusCommand::execute,
                ollamaModelCommand::execute,
                ollamaPromptCommand::execute,
                conversationCommand::execute)
                .withWebSearch(localToolsCommand::execute)
                .withToolCatalog(toolCatalogCommand::execute)
                .route(arguments);
    }

    private static long storeKnowledge(
            String model, List<EmbeddedChunk> chunks) {
        try {
            return new SqliteKnowledgeStore(KnowledgeDatabasePath.load()).store(model, chunks);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw KnowledgeStorageException.unavailable();
        }
    }

    private static List<StoredKnowledgeDocument> loadKnowledge() {
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
