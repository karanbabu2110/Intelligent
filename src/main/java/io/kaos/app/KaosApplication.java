package io.kaos.app;

import io.kaos.ai.ollama.OllamaConnectivity;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaThinkingMode;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.conversation.ConversationDatabasePath;
import io.kaos.conversation.ConversationHistory;
import io.kaos.conversation.ConversationMessage;
import io.kaos.conversation.ConversationRole;
import io.kaos.conversation.ConversationSession;
import io.kaos.conversation.ConversationStorageException;
import io.kaos.conversation.ConversationStorageException.Reason;
import io.kaos.conversation.SqliteConversationSchema;
import io.kaos.conversation.SqliteConversationStore;
import io.kaos.knowledge.DocumentChunk;
import io.kaos.knowledge.DocumentChunker;
import io.kaos.knowledge.IngestedDocument;
import io.kaos.knowledge.KnowledgeIngestionException;
import io.kaos.knowledge.ExtractedText;
import io.kaos.knowledge.PlainTextExtractor;
import io.kaos.knowledge.TextExtractionException;
import io.kaos.knowledge.TextDocumentIngestor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The single process entry point for the evolving KAOS application.
 */
public final class KaosApplication {
    static final int SUCCESS = 0;
    static final int APPLICATION_ERROR = 1;
    static final int USAGE_ERROR = 2;

    static final String INVALID_CONFIGURATION_CODE = "KAOS-CONFIG-001";
    static final String UNREADABLE_CONFIGURATION_CODE = "KAOS-CONFIG-002";
    static final String UNEXPECTED_APPLICATION_CODE = "KAOS-APP-001";
    static final String OLLAMA_CONNECTIVITY_CODE = "KAOS-AI-001";
    static final String INVALID_OLLAMA_MODEL_CODE = "KAOS-AI-CONFIG-001";
    static final String UNREADABLE_OLLAMA_MODEL_CODE = "KAOS-AI-CONFIG-002";
    static final String OLLAMA_PROMPT_CODE = "KAOS-AI-002";
    static final String OLLAMA_RESPONSE_LIMIT_CODE = "KAOS-AI-003";
    static final String OLLAMA_STREAM_CODE = "KAOS-AI-004";
    static final String OLLAMA_TIMEOUT_CODE = "KAOS-AI-005";
    static final String OLLAMA_CANCELLATION_CODE = "KAOS-AI-006";
    static final String CONVERSATION_INPUT_CODE = "KAOS-CONVERSATION-001";
    static final String CONVERSATION_STORAGE_CODE = "KAOS-CONVERSATION-002";
    static final String INVALID_KNOWLEDGE_DOCUMENT_CODE = "KAOS-KNOWLEDGE-001";
    static final String UNAVAILABLE_KNOWLEDGE_DOCUMENT_CODE = "KAOS-KNOWLEDGE-002";
    static final String KNOWLEDGE_DOCUMENT_LIMIT_CODE = "KAOS-KNOWLEDGE-003";
    static final String KNOWLEDGE_TEXT_EXTRACTION_CODE = "KAOS-KNOWLEDGE-004";

    private KaosApplication() {
    }

    public static void main(String[] args) {
        Thread commandThread = Thread.currentThread();
        CountDownLatch commandFinished = new CountDownLatch(1);
        Thread cancellationHook = new Thread(() -> {
            commandThread.interrupt();
            awaitCommandCleanup(commandFinished);
        }, "kaos-command-cancellation");
        Runtime runtime = Runtime.getRuntime();
        boolean hookRegistered = false;
        int exitCode;
        try {
            try {
                runtime.addShutdownHook(cancellationHook);
                hookRegistered = true;
            } catch (IllegalStateException | SecurityException exception) {
                // The normal command boundary still handles direct thread interruption.
            }
            exitCode = launch(
                    args, ApplicationConfiguration::load, System.in, System.out, System.err);
        } finally {
            commandFinished.countDown();
            if (hookRegistered) {
                try {
                    runtime.removeShutdownHook(cancellationHook);
                } catch (IllegalStateException | SecurityException exception) {
                    // Shutdown already owns the hook, or the runtime denied hook removal.
                }
            }
        }
        if (exitCode != SUCCESS && !commandThread.isInterrupted()) {
            System.exit(exitCode);
        }
    }

    private static void awaitCommandCleanup(CountDownLatch commandFinished) {
        try {
            commandFinished.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    static int launch(
            String[] arguments,
            Supplier<ApplicationConfiguration> configurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        return launch(arguments, configurationLoader, InputStream.nullInputStream(),
                output, errorOutput);
    }

    static int launch(
            String[] arguments,
            Supplier<ApplicationConfiguration> configurationLoader,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configurationLoader, "configurationLoader");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(errorOutput, "errorOutput");

        ApplicationConfiguration configuration;
        try {
            configuration = configurationLoader.get();
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_CONFIGURATION_CODE,
                    "Invalid application configuration. Check kaos.app.name or KAOS_APP_NAME and restart.");
            return APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(
                    errorOutput,
                    UNREADABLE_CONFIGURATION_CODE,
                    "Local application configuration could not be read. Check process permissions and restart.");
            return APPLICATION_ERROR;
        } catch (RuntimeException exception) {
            return logUnexpectedApplicationFailure(errorOutput);
        }

        try {
            return run(arguments, configuration, input, output, errorOutput);
        } catch (RuntimeException exception) {
            return logUnexpectedApplicationFailure(errorOutput);
        }
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            PrintStream output,
            PrintStream errorOutput) {
        return run(arguments, configuration, InputStream.nullInputStream(), output, errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        return run(
                arguments,
                configuration,
                () -> new OllamaConnectivity().check(),
                OllamaModelConfiguration::load,
                (model, history, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(
                                model, history, prompt, thinking, chunks),
                input,
                output,
                errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            PrintStream output,
            PrintStream errorOutput) {
        return run(
                arguments,
                configuration,
                ollamaConnectivityCheck,
                OllamaModelConfiguration::load,
                (model, history, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(
                                model, history, prompt, thinking, chunks),
                InputStream.nullInputStream(),
                output,
                errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            Supplier<OllamaModelConfiguration> ollamaModelConfigurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        return run(
                arguments,
                configuration,
                ollamaConnectivityCheck,
                ollamaModelConfigurationLoader,
                (model, history, prompt, thinking, chunks) ->
                        new OllamaPromptClient().submit(
                                model, history, prompt, thinking, chunks),
                InputStream.nullInputStream(),
                output,
                errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            Supplier<OllamaModelConfiguration> ollamaModelConfigurationLoader,
            OllamaPromptSubmission ollamaPromptSubmission,
            PrintStream output,
            PrintStream errorOutput) {
        return run(arguments, configuration, ollamaConnectivityCheck,
                ollamaModelConfigurationLoader, ollamaPromptSubmission,
                InputStream.nullInputStream(), output, errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            Supplier<OllamaModelConfiguration> ollamaModelConfigurationLoader,
            OllamaPromptSubmission ollamaPromptSubmission,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        return run(arguments, configuration, ollamaConnectivityCheck,
                ollamaModelConfigurationLoader, ollamaPromptSubmission,
                ConversationDatabasePath::load, input, output, errorOutput);
    }

    static int run(
            String[] arguments,
            ApplicationConfiguration configuration,
            Supplier<OllamaConnectivity.Result> ollamaConnectivityCheck,
            Supplier<OllamaModelConfiguration> ollamaModelConfigurationLoader,
            OllamaPromptSubmission ollamaPromptSubmission,
            Supplier<Path> conversationDatabasePathLoader,
            InputStream input,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(ollamaConnectivityCheck, "ollamaConnectivityCheck");
        Objects.requireNonNull(ollamaModelConfigurationLoader, "ollamaModelConfigurationLoader");
        Objects.requireNonNull(ollamaPromptSubmission, "ollamaPromptSubmission");
        Objects.requireNonNull(conversationDatabasePathLoader, "conversationDatabasePathLoader");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(errorOutput, "errorOutput");

        if (arguments.length == 0 || isCommand(arguments, "status")) {
            output.println(startupMessage(configuration));
            return SUCCESS;
        }

        if (isCommand(arguments, "help") || isCommand(arguments, "--help")) {
            output.print(helpText());
            return SUCCESS;
        }

        if (arguments.length == 2 && "knowledge-ingest".equals(arguments[0])) {
            return reportKnowledgeIngestion(arguments[1], output, errorOutput);
        }

        if (isCommand(arguments, "ollama-status")) {
            return reportOllamaStatus(ollamaConnectivityCheck.get(), output, errorOutput);
        }

        if (isCommand(arguments, "ollama-model")) {
            return reportOllamaModel(ollamaModelConfigurationLoader, output, errorOutput);
        }

        if (arguments.length == 2 && "ollama-prompt".equals(arguments[0])) {
            return reportOllamaPrompt(
                    arguments[1],
                    ConversationHistory.empty(),
                    ollamaModelConfigurationLoader,
                    ollamaPromptSubmission,
                    output,
                    errorOutput);
        }

        if (isCommand(arguments, "conversation")) {
            return runConversation(input, ollamaModelConfigurationLoader,
                    ollamaPromptSubmission, conversationDatabasePathLoader,
                    output, errorOutput);
        }

        if (arguments.length > 0 && "ollama-prompt".equals(arguments[0])) {
            errorOutput.println("Expected one quoted prompt. Run 'kaos help' for usage.");
            return USAGE_ERROR;
        }

        if (arguments.length > 0 && "knowledge-ingest".equals(arguments[0])) {
            errorOutput.println("Expected one local .txt path. Run 'kaos help' for usage.");
            return USAGE_ERROR;
        }

        errorOutput.println(invalidArgumentsMessage(arguments));
        return USAGE_ERROR;
    }

    static String startupMessage(ApplicationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return configuration.applicationName() + " application baseline is running.";
    }

    static String helpText() {
        return """
                Usage: kaos [status|help|knowledge-ingest <path>|ollama-status|ollama-model|ollama-prompt <prompt>|conversation]

                Commands:
                  status         Show local application status (default).
                  help           Show this help. The --help alias is also supported.
                  knowledge-ingest  Admit one local UTF-8 .txt document up to 1 MiB.
                  ollama-status  Check connectivity to the local Ollama server.
                  ollama-model   Show the explicitly configured local Ollama model.
                  ollama-prompt  Submit one quoted prompt and stream the answer.
                  conversation   Start selectable persistent local conversations.
                """;
    }

    private static int reportKnowledgeIngestion(
            String pathText, PrintStream output, PrintStream errorOutput) {
        try {
            IngestedDocument document = new TextDocumentIngestor().ingest(Path.of(pathText));
            ExtractedText extractedText = new PlainTextExtractor().extract(document);
            List<DocumentChunk> chunks = new DocumentChunker().chunk(extractedText);
            output.println("Ingested document: " + document.name()
                    + " (type: " + document.mediaType()
                    + ", bytes: " + document.byteCount()
                    + ", characters: " + extractedText.codePointCount()
                    + ", chunks: " + chunks.size() + ").");
            return SUCCESS;
        } catch (java.nio.file.InvalidPathException exception) {
            logError(errorOutput, INVALID_KNOWLEDGE_DOCUMENT_CODE,
                    "Expected one readable, non-empty UTF-8 .txt file. Check the file and retry.");
            return APPLICATION_ERROR;
        } catch (KnowledgeIngestionException exception) {
            String code;
            String recovery;
            switch (exception.reason()) {
                case INVALID_DOCUMENT -> {
                    code = INVALID_KNOWLEDGE_DOCUMENT_CODE;
                    recovery = "Expected one readable, non-empty UTF-8 .txt file. "
                            + "Check the file and retry.";
                }
                case UNAVAILABLE -> {
                    code = UNAVAILABLE_KNOWLEDGE_DOCUMENT_CODE;
                    recovery = "The local text document could not be read. "
                            + "Check that it exists and is accessible, then retry.";
                }
                case TOO_LARGE -> {
                    code = KNOWLEDGE_DOCUMENT_LIMIT_CODE;
                    recovery = "The local text document exceeds the 1 MiB ingestion limit. "
                            + "Choose a smaller file and retry.";
                }
                default -> throw new IllegalStateException("Unknown ingestion reason.");
            }
            logError(errorOutput, code, recovery);
            return APPLICATION_ERROR;
        } catch (TextExtractionException exception) {
            logError(errorOutput, KNOWLEDGE_TEXT_EXTRACTION_CODE,
                    "The admitted document could not be extracted as bounded UTF-8 text. "
                            + "Check the file content and retry.");
            return APPLICATION_ERROR;
        }
    }

    private static int runConversation(
            InputStream input,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            Supplier<Path> databasePathLoader,
            PrintStream output,
            PrintStream errorOutput) {
        ConversationRuntime conversationRuntime;
        try {
            conversationRuntime = restoreConversationRuntime(databasePathLoader.get());
        } catch (ConversationStorageException exception) {
            logConversationStorageFailure(
                    errorOutput, exception.reason(), ConversationStoragePhase.STARTUP);
            return APPLICATION_ERROR;
        } catch (IOException | IllegalStateException | SecurityException exception) {
            logConversationStorageFailure(
                    errorOutput, Reason.UNAVAILABLE, ConversationStoragePhase.STARTUP);
            return APPLICATION_ERROR;
        } catch (IllegalArgumentException exception) {
            logConversationStorageFailure(
                    errorOutput, Reason.INVALID_STATE, ConversationStoragePhase.STARTUP);
            return APPLICATION_ERROR;
        }
        ConversationSession session = conversationRuntime.session();
        SqliteConversationStore store = conversationRuntime.store();
        if (conversationRuntime.restoredCount() == 0) {
            output.println("Conversation " + session.activeIdentifier()
                    + " created and selected.");
        } else {
            output.println("Restored " + conversationRuntime.restoredCount()
                    + (conversationRuntime.restoredCount() == 1
                            ? " conversation. "
                            : " conversations. ")
                    + "Conversation " + session.activeIdentifier() + " selected.");
        }
        output.println("Type /help for conversation controls.");
        int sessionExitCode = SUCCESS;

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                input,
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)));
        while (true) {
            output.print("kaos[" + session.activeIdentifier() + "]> ");
            output.flush();

            String line;
            try {
                line = reader.readLine();
            } catch (IOException exception) {
                output.println();
                logError(errorOutput, CONVERSATION_INPUT_CODE,
                        "Conversation input could not be read. Exit and retry.");
                return APPLICATION_ERROR;
            }

            if (line == null) {
                output.println();
                output.println("Conversation session ended.");
                return sessionExitCode;
            }

            String command = line.strip();
            if (command.isEmpty()) {
                errorOutput.println("Enter a prompt or conversation control. Type /help.");
                continue;
            }
            if ("/exit".equals(command)) {
                output.println("Conversation session ended.");
                return sessionExitCode;
            }
            if ("/help".equals(command)) {
                output.print(conversationHelpText());
                continue;
            }
            if ("/new".equals(command)) {
                if (!session.canCreate()) {
                    errorOutput.println("Conversation limit reached ("
                            + ConversationSession.MAX_CONVERSATIONS
                            + "). Select an existing conversation or exit and restart.");
                    continue;
                }
                long identifier = session.create();
                try {
                    store.store(identifier);
                } catch (ConversationStorageException exception) {
                    logConversationStorageFailure(errorOutput, exception.reason(),
                            ConversationStoragePhase.CONVERSATION_CREATE);
                    return APPLICATION_ERROR;
                }
                output.println("Conversation " + identifier + " created and selected.");
                continue;
            }
            if ("/list".equals(command)) {
                output.println(conversationList(session));
                continue;
            }
            if (command.startsWith("/select")) {
                selectConversation(command, session, output, errorOutput);
                continue;
            }
            if (command.startsWith("/")) {
                errorOutput.println("Unknown conversation control. Type /help.");
                continue;
            }
            if (!session.canAppendTurn()) {
                errorOutput.println("Conversation turn limit reached ("
                        + ConversationSession.MAX_TURNS_PER_CONVERSATION
                        + "). Select another conversation with capacity or exit and restart.");
                continue;
            }

            PromptOutcome outcome = submitOllamaPrompt(
                    line, session.activeHistory(), modelConfigurationLoader,
                    promptSubmission, output, errorOutput);
            if (outcome.exitCode() != SUCCESS) {
                sessionExitCode = mergeSessionExitCode(
                        sessionExitCode, outcome.exitCode());
                if (Thread.currentThread().isInterrupted()) {
                    return sessionExitCode;
                }
                continue;
            }
            try {
                store.appendMessages(session.activeIdentifier(), List.of(
                        new ConversationMessage(ConversationRole.USER, outcome.prompt()),
                        new ConversationMessage(ConversationRole.ASSISTANT, outcome.response())));
            } catch (ConversationStorageException exception) {
                logConversationStorageFailure(errorOutput, exception.reason(),
                        ConversationStoragePhase.TURN_WRITE);
                return APPLICATION_ERROR;
            }
            session.appendTurn(outcome.prompt(), outcome.response());
        }
    }

    private static ConversationRuntime restoreConversationRuntime(Path databasePath)
            throws IOException {
        Objects.requireNonNull(databasePath, "databasePath");
        Path parent = databasePath.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IllegalArgumentException("conversation database parent is unavailable");
        }
        Files.createDirectories(parent);
        if (Files.exists(databasePath) && !Files.isRegularFile(databasePath)) {
            throw new IllegalArgumentException("conversation database target is not a regular file");
        }

        SqliteConversationSchema.initialize(databasePath);
        SqliteConversationStore store = new SqliteConversationStore(databasePath);
        Map<Long, ConversationHistory> restoredHistories = new LinkedHashMap<>();
        for (long identifier : store.recentConversationIdentifiers(
                ConversationSession.MAX_CONVERSATIONS)) {
            restoredHistories.put(identifier, store.conversationHistory(identifier));
        }
        ConversationSession session = ConversationSession.restore(
                restoredHistories, store.greatestConversationIdentifier());
        if (restoredHistories.isEmpty()) {
            long identifier = session.create();
            store.store(identifier);
        }
        return new ConversationRuntime(store, session, restoredHistories.size());
    }

    private static void logConversationStorageFailure(
            PrintStream errorOutput,
            Reason reason,
            ConversationStoragePhase phase) {
        logError(errorOutput, CONVERSATION_STORAGE_CODE,
                conversationStorageRecovery(reason, phase));
    }

    static String conversationStorageRecovery(
            Reason reason,
            ConversationStoragePhase phase) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(phase, "phase");
        String recovery = switch (reason) {
            case LOCKED ->
                    "Local conversation storage is locked. Close other processes using "
                            + "conversations.db and retry.";
            case CORRUPT ->
                    "Local conversation storage is corrupt or is not a SQLite database. "
                            + "Stop KAOS and make an offline copy of conversations.db before "
                            + "attempting repair.";
            case READ_ONLY ->
                    "Local conversation storage is read-only. Grant the current account write "
                            + "access or select a writable KAOS data directory, then retry.";
            case CAPACITY ->
                    "Local conversation storage has insufficient capacity. Free local disk "
                            + "space and retry.";
            case UNAVAILABLE ->
                    "Local conversation storage is unavailable. Check the configured KAOS data "
                            + "directory and process permissions, then retry.";
            case INVALID_STATE ->
                    "Local conversation storage contains an unsupported or inconsistent state. "
                            + "Make an offline copy, verify the configured KAOS data directory, "
                            + "and retry without replacing the original.";
            case UNKNOWN ->
                    "Local conversation storage failed for an unknown reason. Stop KAOS, keep "
                            + "the existing database, and verify the configured data directory "
                            + "before retrying.";
        };
        return recovery + switch (phase) {
            case STARTUP ->
                    " KAOS did not delete, replace, or automatically repair the database.";
            case CONVERSATION_CREATE ->
                    " The new conversation was not saved; this command is ending to avoid "
                            + "divergent state.";
            case TURN_WRITE ->
                    " The completed turn was not saved even though its answer was displayed; "
                            + "this command is ending to avoid divergent state.";
        };
    }

    private static int mergeSessionExitCode(int current, int next) {
        if (current == APPLICATION_ERROR || next == APPLICATION_ERROR) {
            return APPLICATION_ERROR;
        }
        return current == USAGE_ERROR || next == USAGE_ERROR ? USAGE_ERROR : SUCCESS;
    }

    private static void selectConversation(
            String command,
            ConversationSession session,
            PrintStream output,
            PrintStream errorOutput) {
        String[] parts = command.split("\\s+");
        long identifier;
        try {
            if (parts.length != 2) {
                throw new NumberFormatException();
            }
            identifier = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            errorOutput.println("Expected /select <existing-id>. Type /list to see conversations.");
            return;
        }

        if (!session.select(identifier)) {
            errorOutput.println("Conversation does not exist. Type /list to see conversations.");
            return;
        }
        output.println("Conversation " + identifier + " selected.");
    }

    private static String conversationList(ConversationSession session) {
        StringBuilder list = new StringBuilder("Conversations:");
        long activeIdentifier = session.activeIdentifier();
        for (long identifier : session.conversationIdentifiers()) {
            list.append(identifier == activeIdentifier ? " *" : " ").append(identifier);
        }
        return list.toString();
    }

    private static String conversationHelpText() {
        return """
                Conversation controls:
                  /new          Create and select a new conversation.
                  /select <id>  Select an existing conversation.
                  /list         List conversations; * marks the selected one.
                  /help         Show these controls.
                  /exit         End the session; clean turns remain stored locally.
                Any other nonblank line is sent as a prompt.
                Limits: %d conversations and %d clean turns per conversation.
                """.formatted(ConversationSession.MAX_CONVERSATIONS,
                        ConversationSession.MAX_TURNS_PER_CONVERSATION);
    }

    private static int reportOllamaPrompt(
            String promptText,
            ConversationHistory history,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            PrintStream output,
            PrintStream errorOutput) {
        return submitOllamaPrompt(promptText, history, modelConfigurationLoader,
                promptSubmission, output, errorOutput).exitCode();
    }

    private static PromptOutcome submitOllamaPrompt(
            String promptText,
            ConversationHistory history,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            OllamaPromptSubmission promptSubmission,
            PrintStream output,
            PrintStream errorOutput) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(promptText);
        } catch (IllegalArgumentException exception) {
            errorOutput.println("Expected one valid quoted prompt. Run 'kaos help' for usage.");
            return PromptOutcome.failed(USAGE_ERROR);
        }

        OllamaModelConfiguration model;
        try {
            model = modelConfigurationLoader.get();
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_OLLAMA_MODEL_CODE,
                    invalidOllamaConfigurationGuidance());
            return PromptOutcome.failed(APPLICATION_ERROR);
        } catch (IllegalStateException exception) {
            logError(
                    errorOutput,
                    UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return PromptOutcome.failed(APPLICATION_ERROR);
        }

        OllamaPromptOutput promptOutput = new OllamaPromptOutput(model.thinkingMode(), output);
        OllamaPromptClient.Result result = promptSubmission.submit(
                model, history, prompt, promptOutput::thinkingStarted, promptOutput::answerChunk);
        if (result.successful()) {
            output.println();
            return PromptOutcome.success(prompt.text(), result.response());
        }

        boolean partialOutput = promptOutput.finishFailure();
        String recovery = switch (result.status()) {
            case TOKEN_LIMIT_REACHED ->
                    "Ollama reached a response or context length boundary before completing the "
                            + "answer. Review the response-token limit and context window, then "
                            + "retry.";
            case LOCAL_LIMIT_REACHED ->
                    "KAOS stopped the Ollama stream at a local byte or text safety limit. "
                            + "Shorten the request or response, then retry.";
            case UNAVAILABLE ->
                    "Local Ollama could not be reached before the prompt response began. "
                            + "Start Ollama on 127.0.0.1:11434 and retry.";
            case REQUEST_FAILED ->
                    "Local Ollama rejected the prompt request. Verify the configured model and retry.";
            case INVALID_RESPONSE ->
                    "Local Ollama returned an invalid prompt response. Verify Ollama and retry.";
            case STREAM_FAILED ->
                    "The accepted Ollama response stream lost its local connection. "
                            + "Verify Ollama is still running, then retry.";
            case TOTAL_TIMEOUT ->
                    "The Ollama prompt exceeded its five-minute total deadline. Shorten the "
                            + "request or select a faster local model, then retry.";
            case INACTIVITY_TIMEOUT ->
                    "The Ollama response stream produced no data for 60 seconds. Verify Ollama "
                            + "is still progressing or select a faster local model, then retry.";
            case INTERRUPTED ->
                    "The Ollama prompt request was cancelled. Retry when ready.";
            case SUCCESS -> throw new IllegalStateException("Successful result has no response.");
        };
        if (partialOutput) {
            recovery = "Partial streaming output was displayed before clean completion. "
                    + recovery;
        }
        String errorCode = switch (result.status()) {
            case UNAVAILABLE -> OLLAMA_CONNECTIVITY_CODE;
            case REQUEST_FAILED, INVALID_RESPONSE -> OLLAMA_PROMPT_CODE;
            case TOKEN_LIMIT_REACHED, LOCAL_LIMIT_REACHED -> OLLAMA_RESPONSE_LIMIT_CODE;
            case STREAM_FAILED -> OLLAMA_STREAM_CODE;
            case TOTAL_TIMEOUT, INACTIVITY_TIMEOUT -> OLLAMA_TIMEOUT_CODE;
            case INTERRUPTED -> OLLAMA_CANCELLATION_CODE;
            case SUCCESS -> throw new IllegalStateException("Successful result has no error code.");
        };
        logError(errorOutput, errorCode, recovery);
        return PromptOutcome.failed(APPLICATION_ERROR);
    }

    @FunctionalInterface
    interface OllamaPromptSubmission {
        OllamaPromptClient.Result submit(
                OllamaModelConfiguration model,
                ConversationHistory history,
                OllamaPrompt prompt,
                Runnable thinkingStarted,
                Consumer<String> answerChunkConsumer);
    }

    private record PromptOutcome(int exitCode, String prompt, String response) {
        private PromptOutcome {
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(response, "response");
        }

        private static PromptOutcome success(String prompt, String response) {
            return new PromptOutcome(SUCCESS, prompt, response);
        }

        private static PromptOutcome failed(int exitCode) {
            return new PromptOutcome(exitCode, "", "");
        }
    }

    private record ConversationRuntime(
            SqliteConversationStore store,
            ConversationSession session,
            int restoredCount) {
    }

    enum ConversationStoragePhase {
        STARTUP,
        CONVERSATION_CREATE,
        TURN_WRITE
    }

    private static final class OllamaPromptOutput {
        private final OllamaThinkingMode thinkingMode;
        private final PrintStream output;
        private boolean thinkingVisible;
        private boolean answerVisible;
        private boolean answerContentVisible;
        private boolean answerEndsWithLineBreak;

        private OllamaPromptOutput(OllamaThinkingMode thinkingMode, PrintStream output) {
            this.thinkingMode = Objects.requireNonNull(thinkingMode, "thinkingMode");
            this.output = Objects.requireNonNull(output, "output");
        }

        private void thinkingStarted() {
            if (thinkingMode == OllamaThinkingMode.ON && !thinkingVisible) {
                output.println("Thinking...");
                output.flush();
                thinkingVisible = true;
            }
        }

        private void answerChunk(String chunk) {
            if (thinkingMode == OllamaThinkingMode.ON && !answerVisible) {
                output.println("Answer:");
                answerVisible = true;
            }
            output.print(chunk);
            output.flush();
            if (!chunk.isEmpty()) {
                answerContentVisible = true;
                answerEndsWithLineBreak = chunk.endsWith("\n") || chunk.endsWith("\r");
            }
        }

        private boolean finishFailure() {
            if (answerContentVisible && !answerEndsWithLineBreak) {
                output.println();
                output.flush();
            }
            return thinkingVisible || answerContentVisible;
        }
    }

    private static int reportOllamaModel(
            Supplier<OllamaModelConfiguration> configurationLoader,
            PrintStream output,
            PrintStream errorOutput) {
        try {
            OllamaModelConfiguration configuration = configurationLoader.get();
            output.println("Configured local Ollama model: " + configuration.modelName()
                    + " (context window: " + configuration.contextWindow()
                    + " tokens, thinking: "
                    + configuration.thinkingMode().configurationValue()
                    + ", response limit: " + configuration.responseTokenLimit()
                    + " tokens).");
            return SUCCESS;
        } catch (IllegalArgumentException exception) {
            logError(
                    errorOutput,
                    INVALID_OLLAMA_MODEL_CODE,
                    invalidOllamaConfigurationGuidance());
            return APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            logError(
                    errorOutput,
                    UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return APPLICATION_ERROR;
        }
    }

    private static String invalidOllamaConfigurationGuidance() {
        return "Invalid Ollama configuration. Check model, context-window, thinking, and "
                + "response-token-limit process settings and retry.";
    }

    private static int reportOllamaStatus(
            OllamaConnectivity.Result result,
            PrintStream output,
            PrintStream errorOutput) {
        Objects.requireNonNull(result, "result");
        if (result.reachable()) {
            output.println("Local Ollama is reachable (version " + result.version() + ").");
            return SUCCESS;
        }

        String recovery = switch (result.status()) {
            case UNAVAILABLE ->
                    "Local Ollama is unavailable. Start Ollama on 127.0.0.1:11434 and retry.";
            case INVALID_RESPONSE ->
                    "Local Ollama returned an invalid version response. Verify Ollama and retry.";
            case INTERRUPTED ->
                    "The Ollama connectivity check was interrupted. Retry the command.";
            case REACHABLE -> throw new IllegalStateException("Reachable result has no version.");
        };
        logError(errorOutput, OLLAMA_CONNECTIVITY_CODE, recovery);
        return APPLICATION_ERROR;
    }

    private static boolean isCommand(String[] arguments, String command) {
        return arguments.length == 1 && command.equals(arguments[0]);
    }

    private static String invalidArgumentsMessage(String[] arguments) {
        if (arguments.length > 1) {
            return "Expected at most one command. Run 'kaos help' for usage.";
        }
        return "Unknown command. Run 'kaos help' for usage.";
    }

    private static void logError(PrintStream errorOutput, String code, String message) {
        errorOutput.println("ERROR [" + code + "] " + message);
    }

    private static int logUnexpectedApplicationFailure(PrintStream errorOutput) {
        logError(
                errorOutput,
                UNEXPECTED_APPLICATION_CODE,
                "KAOS could not complete the requested command. Restart and retry.");
        return APPLICATION_ERROR;
    }
}
