package io.kaos.app;

import io.kaos.conversation.ConversationHistory;
import io.kaos.conversation.ConversationMessage;
import io.kaos.conversation.ConversationRole;
import io.kaos.conversation.ConversationSession;
import io.kaos.conversation.ConversationStorageException;
import io.kaos.conversation.ConversationStorageException.Reason;
import io.kaos.conversation.SqliteConversationSchema;
import io.kaos.conversation.SqliteConversationStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Runs the interactive conversation and coordinates its durable local state. */
final class ConversationCommand {
    private final CommandContext context;
    private final OllamaCommands ollamaCommands;
    private final Supplier<Path> databasePathLoader;

    ConversationCommand(CommandContext context, OllamaCommands ollamaCommands,
            Supplier<Path> databasePathLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.ollamaCommands = Objects.requireNonNull(ollamaCommands, "ollamaCommands");
        this.databasePathLoader = Objects.requireNonNull(databasePathLoader, "databasePathLoader");
    }

    int execute() {
        ConversationRuntime runtime;
        try {
            runtime = restoreRuntime(databasePathLoader.get());
        } catch (ConversationStorageException exception) {
            logStorageFailure(exception.reason(), StoragePhase.STARTUP);
            return KaosApplication.APPLICATION_ERROR;
        } catch (IOException | IllegalStateException | SecurityException exception) {
            logStorageFailure(Reason.UNAVAILABLE, StoragePhase.STARTUP);
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalArgumentException exception) {
            logStorageFailure(Reason.INVALID_STATE, StoragePhase.STARTUP);
            return KaosApplication.APPLICATION_ERROR;
        }
        ConversationSession session = runtime.session();
        SqliteConversationStore store = runtime.store();
        if (runtime.restoredCount() == 0) {
            context.output().println("Conversation " + session.activeIdentifier()
                    + " created and selected.");
        } else {
            context.output().println("Restored " + runtime.restoredCount()
                    + (runtime.restoredCount() == 1 ? " conversation. " : " conversations. ")
                    + "Conversation " + session.activeIdentifier() + " selected.");
        }
        context.output().println("Type /help for conversation controls.");
        int sessionExitCode = KaosApplication.SUCCESS;
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.input(), StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)));
        while (true) {
            context.output().print("kaos[" + session.activeIdentifier() + "]> ");
            context.output().flush();
            String line;
            try {
                line = reader.readLine();
            } catch (IOException exception) {
                context.output().println();
                logError(KaosApplication.CONVERSATION_INPUT_CODE,
                        "Conversation input could not be read. Exit and retry.");
                return KaosApplication.APPLICATION_ERROR;
            }
            if (line == null) {
                context.output().println();
                context.output().println("Conversation session ended.");
                return sessionExitCode;
            }
            String command = line.strip();
            if (command.isEmpty()) {
                context.errorOutput().println("Enter a prompt or conversation control. Type /help.");
                continue;
            }
            if ("/exit".equals(command)) {
                context.output().println("Conversation session ended.");
                return sessionExitCode;
            }
            if ("/help".equals(command)) {
                context.output().print(helpText());
                continue;
            }
            if ("/new".equals(command)) {
                if (!session.canCreate()) {
                    context.errorOutput().println("Conversation limit reached ("
                            + ConversationSession.MAX_CONVERSATIONS
                            + "). Select an existing conversation or exit and restart.");
                    continue;
                }
                long identifier = session.create();
                try {
                    store.store(identifier);
                } catch (ConversationStorageException exception) {
                    logStorageFailure(exception.reason(), StoragePhase.CONVERSATION_CREATE);
                    return KaosApplication.APPLICATION_ERROR;
                }
                context.output().println("Conversation " + identifier + " created and selected.");
                continue;
            }
            if ("/list".equals(command)) {
                context.output().println(conversationList(session));
                continue;
            }
            if (command.startsWith("/select")) {
                selectConversation(command, session);
                continue;
            }
            if (command.startsWith("/")) {
                context.errorOutput().println("Unknown conversation control. Type /help.");
                continue;
            }
            if (!session.canAppendTurn()) {
                context.errorOutput().println("Conversation turn limit reached ("
                        + ConversationSession.MAX_TURNS_PER_CONVERSATION
                        + "). Select another conversation with capacity or exit and restart.");
                continue;
            }
            OllamaCommands.PromptOutcome outcome = ollamaCommands.submitPrompt(
                    line, session.activeHistory());
            if (outcome.exitCode() != KaosApplication.SUCCESS) {
                sessionExitCode = mergeExitCode(sessionExitCode, outcome.exitCode());
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
                logStorageFailure(exception.reason(), StoragePhase.TURN_WRITE);
                return KaosApplication.APPLICATION_ERROR;
            }
            session.appendTurn(outcome.prompt(), outcome.response());
        }
    }

    private static ConversationRuntime restoreRuntime(Path databasePath) throws IOException {
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
        Map<Long, ConversationHistory> histories = new LinkedHashMap<>();
        for (long identifier : store.recentConversationIdentifiers(
                ConversationSession.MAX_CONVERSATIONS)) {
            histories.put(identifier, store.conversationHistory(identifier));
        }
        ConversationSession session = ConversationSession.restore(
                histories, store.greatestConversationIdentifier());
        if (histories.isEmpty()) {
            long identifier = session.create();
            store.store(identifier);
        }
        return new ConversationRuntime(store, session, histories.size());
    }

    static String storageRecovery(Reason reason, StoragePhase phase) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(phase, "phase");
        String recovery = switch (reason) {
            case LOCKED -> "Local conversation storage is locked. Close other processes using "
                    + "conversations.db and retry.";
            case CORRUPT -> "Local conversation storage is corrupt or is not a SQLite database. "
                    + "Stop KAOS and make an offline copy of conversations.db before attempting repair.";
            case READ_ONLY -> "Local conversation storage is read-only. Grant the current account "
                    + "write access or select a writable KAOS data directory, then retry.";
            case CAPACITY -> "Local conversation storage has insufficient capacity. Free local disk "
                    + "space and retry.";
            case UNAVAILABLE -> "Local conversation storage is unavailable. Check the configured KAOS "
                    + "data directory and process permissions, then retry.";
            case INVALID_STATE -> "Local conversation storage contains an unsupported or inconsistent "
                    + "state. Make an offline copy, verify the configured KAOS data directory, "
                    + "and retry without replacing the original.";
            case UNKNOWN -> "Local conversation storage failed for an unknown reason. Stop KAOS, keep "
                    + "the existing database, and verify the configured data directory before retrying.";
        };
        return recovery + switch (phase) {
            case STARTUP -> " KAOS did not delete, replace, or automatically repair the database.";
            case CONVERSATION_CREATE -> " The new conversation was not saved; this command is ending "
                    + "to avoid divergent state.";
            case TURN_WRITE -> " The completed turn was not saved even though its answer was displayed; "
                    + "this command is ending to avoid divergent state.";
        };
    }

    private void logStorageFailure(Reason reason, StoragePhase phase) {
        logError(KaosApplication.CONVERSATION_STORAGE_CODE, storageRecovery(reason, phase));
    }

    private void logError(String code, String message) {
        context.errorOutput().println("ERROR [" + code + "] " + message);
    }

    private static int mergeExitCode(int current, int next) {
        if (current == KaosApplication.APPLICATION_ERROR || next == KaosApplication.APPLICATION_ERROR) {
            return KaosApplication.APPLICATION_ERROR;
        }
        return current == KaosApplication.USAGE_ERROR || next == KaosApplication.USAGE_ERROR
                ? KaosApplication.USAGE_ERROR : KaosApplication.SUCCESS;
    }

    private void selectConversation(String command, ConversationSession session) {
        String[] parts = command.split("\\s+");
        long identifier;
        try {
            if (parts.length != 2) {
                throw new NumberFormatException();
            }
            identifier = Long.parseLong(parts[1]);
        } catch (NumberFormatException exception) {
            context.errorOutput().println(
                    "Expected /select <existing-id>. Type /list to see conversations.");
            return;
        }
        if (!session.select(identifier)) {
            context.errorOutput().println("Conversation does not exist. Type /list to see conversations.");
            return;
        }
        context.output().println("Conversation " + identifier + " selected.");
    }

    private static String conversationList(ConversationSession session) {
        StringBuilder list = new StringBuilder("Conversations:");
        long activeIdentifier = session.activeIdentifier();
        for (long identifier : session.conversationIdentifiers()) {
            list.append(identifier == activeIdentifier ? " *" : " ").append(identifier);
        }
        return list.toString();
    }

    private static String helpText() {
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

    enum StoragePhase { STARTUP, CONVERSATION_CREATE, TURN_WRITE }

    private record ConversationRuntime(
            SqliteConversationStore store, ConversationSession session, int restoredCount) {
    }
}
