package io.kaos.app;

import java.util.Objects;

/**
 * Parses the public command-line shape and dispatches one application command.
 */
final class CommandRouter {
    private final CommandContext context;
    private final ArgumentCommand knowledgeIngestCommand;
    private final ArgumentCommand knowledgeRetrieveCommand;
    private final ArgumentCommand knowledgeAskCommand;
    private final TwoArgumentCommand memoryCreateCommand;
    private final ArgumentCommand memoryInspectCommand;
    private final TwoArgumentCommand memoryEditCommand;
    private final ArgumentCommand memoryDeleteCommand;
    private final Command memoryPrivacyCommand;
    private final ArgumentCommand readLocalFileCommand;
    private final ArgumentCommand httpGetCommand;
    private ArgumentCommand webSearchCommand = argument -> KaosApplication.USAGE_ERROR;
    private ArgumentCommand agentCommand = argument -> KaosApplication.USAGE_ERROR;
    private Command toolCatalogCommand = () -> KaosApplication.USAGE_ERROR;
    private Command toolHistoryCommand = () -> KaosApplication.USAGE_ERROR;

    CommandRouter withWebSearch(ArgumentCommand command) {
        webSearchCommand = Objects.requireNonNull(command);
        return this;
    }

    CommandRouter withAgent(ArgumentCommand command) {
        agentCommand = Objects.requireNonNull(command);
        return this;
    }

    CommandRouter withToolCatalog(Command command) {
        toolCatalogCommand = Objects.requireNonNull(command);
        return this;
    }

    CommandRouter withToolHistory(Command command) {
        toolHistoryCommand = Objects.requireNonNull(command);
        return this;
    }
    private final Command ollamaStatusCommand;
    private final Command ollamaModelCommand;
    private final ArgumentCommand ollamaPromptCommand;
    private final Command conversationCommand;

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, argument -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                () -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, knowledgeRetrieveCommand,
                argument -> KaosApplication.USAGE_ERROR,
                (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                () -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            ArgumentCommand knowledgeAskCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, knowledgeRetrieveCommand,
                knowledgeAskCommand, (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                () -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            ArgumentCommand knowledgeAskCommand,
            TwoArgumentCommand memoryCreateCommand,
            ArgumentCommand memoryInspectCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, knowledgeRetrieveCommand, knowledgeAskCommand,
                memoryCreateCommand, memoryInspectCommand,
                (first, second) -> KaosApplication.USAGE_ERROR,
                argument -> KaosApplication.USAGE_ERROR,
                () -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            ArgumentCommand knowledgeAskCommand,
            TwoArgumentCommand memoryCreateCommand,
            ArgumentCommand memoryInspectCommand,
            TwoArgumentCommand memoryEditCommand,
            ArgumentCommand memoryDeleteCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, knowledgeRetrieveCommand, knowledgeAskCommand,
                memoryCreateCommand, memoryInspectCommand, memoryEditCommand,
                memoryDeleteCommand, () -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            ArgumentCommand knowledgeAskCommand,
            TwoArgumentCommand memoryCreateCommand,
            ArgumentCommand memoryInspectCommand,
            TwoArgumentCommand memoryEditCommand,
            ArgumentCommand memoryDeleteCommand,
            Command memoryPrivacyCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, knowledgeRetrieveCommand, knowledgeAskCommand,
                memoryCreateCommand, memoryInspectCommand, memoryEditCommand,
                memoryDeleteCommand, memoryPrivacyCommand,
                argument -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            ArgumentCommand knowledgeAskCommand,
            TwoArgumentCommand memoryCreateCommand,
            ArgumentCommand memoryInspectCommand,
            TwoArgumentCommand memoryEditCommand,
            ArgumentCommand memoryDeleteCommand,
            Command memoryPrivacyCommand,
            ArgumentCommand readLocalFileCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this(context, knowledgeIngestCommand, knowledgeRetrieveCommand, knowledgeAskCommand,
                memoryCreateCommand, memoryInspectCommand, memoryEditCommand,
                memoryDeleteCommand, memoryPrivacyCommand, readLocalFileCommand,
                argument -> KaosApplication.USAGE_ERROR,
                ollamaStatusCommand, ollamaModelCommand, ollamaPromptCommand,
                conversationCommand);
    }

    CommandRouter(
            CommandContext context,
            ArgumentCommand knowledgeIngestCommand,
            ArgumentCommand knowledgeRetrieveCommand,
            ArgumentCommand knowledgeAskCommand,
            TwoArgumentCommand memoryCreateCommand,
            ArgumentCommand memoryInspectCommand,
            TwoArgumentCommand memoryEditCommand,
            ArgumentCommand memoryDeleteCommand,
            Command memoryPrivacyCommand,
            ArgumentCommand readLocalFileCommand,
            ArgumentCommand httpGetCommand,
            Command ollamaStatusCommand,
            Command ollamaModelCommand,
            ArgumentCommand ollamaPromptCommand,
            Command conversationCommand) {
        this.context = Objects.requireNonNull(context, "context");
        this.knowledgeIngestCommand = Objects.requireNonNull(
                knowledgeIngestCommand, "knowledgeIngestCommand");
        this.knowledgeRetrieveCommand = Objects.requireNonNull(
                knowledgeRetrieveCommand, "knowledgeRetrieveCommand");
        this.knowledgeAskCommand = Objects.requireNonNull(
                knowledgeAskCommand, "knowledgeAskCommand");
        this.memoryCreateCommand = Objects.requireNonNull(
                memoryCreateCommand, "memoryCreateCommand");
        this.memoryInspectCommand = Objects.requireNonNull(
                memoryInspectCommand, "memoryInspectCommand");
        this.memoryEditCommand = Objects.requireNonNull(memoryEditCommand, "memoryEditCommand");
        this.memoryDeleteCommand = Objects.requireNonNull(
                memoryDeleteCommand, "memoryDeleteCommand");
        this.memoryPrivacyCommand = Objects.requireNonNull(
                memoryPrivacyCommand, "memoryPrivacyCommand");
        this.readLocalFileCommand = Objects.requireNonNull(
                readLocalFileCommand, "readLocalFileCommand");
        this.httpGetCommand = Objects.requireNonNull(httpGetCommand, "httpGetCommand");
        this.ollamaStatusCommand = Objects.requireNonNull(
                ollamaStatusCommand, "ollamaStatusCommand");
        this.ollamaModelCommand = Objects.requireNonNull(
                ollamaModelCommand, "ollamaModelCommand");
        this.ollamaPromptCommand = Objects.requireNonNull(
                ollamaPromptCommand, "ollamaPromptCommand");
        this.conversationCommand = Objects.requireNonNull(
                conversationCommand, "conversationCommand");
    }

    int route(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");

        if (arguments.length == 0 || isCommand(arguments, "status")) {
            context.output().println(KaosApplication.startupMessage(context.configuration()));
            return KaosApplication.SUCCESS;
        }
        if (isCommand(arguments, "help") || isCommand(arguments, "--help")) {
            context.output().print(KaosApplication.helpText());
            return KaosApplication.SUCCESS;
        }
        if (isCommand(arguments, "tools")) {
            return toolCatalogCommand.execute();
        }
        if (isCommand(arguments, "tool-history")) {
            return toolHistoryCommand.execute();
        }
        if (arguments.length == 2 && "knowledge-ingest".equals(arguments[0])) {
            return knowledgeIngestCommand.execute(arguments[1]);
        }
        if (arguments.length == 2 && "knowledge-retrieve".equals(arguments[0])) {
            return knowledgeRetrieveCommand.execute(arguments[1]);
        }
        if (arguments.length == 2 && "knowledge-ask".equals(arguments[0])) {
            return knowledgeAskCommand.execute(arguments[1]);
        }
        if (arguments.length == 3 && "memory-create".equals(arguments[0])) {
            return memoryCreateCommand.execute(arguments[1], arguments[2]);
        }
        if (arguments.length == 2 && "memory-inspect".equals(arguments[0])) {
            return memoryInspectCommand.execute(arguments[1]);
        }
        if (arguments.length == 3 && "memory-edit".equals(arguments[0])) {
            return memoryEditCommand.execute(arguments[1], arguments[2]);
        }
        if (arguments.length == 2 && "memory-delete".equals(arguments[0])) {
            return memoryDeleteCommand.execute(arguments[1]);
        }
        if (isCommand(arguments, "memory-privacy")) {
            return memoryPrivacyCommand.execute();
        }
        if (arguments.length == 2 && "read-local-file".equals(arguments[0])) {
            return readLocalFileCommand.execute(arguments[1]);
        }
        if (arguments.length == 2 && "http-get".equals(arguments[0])) {
            return httpGetCommand.execute(arguments[1]);
        }
        if (arguments.length == 2 && "web-search".equals(arguments[0])) {
            return webSearchCommand.execute(arguments[1]);
        }
        if (arguments.length == 2 && "agent".equals(arguments[0])) {
            return agentCommand.execute(arguments[1]);
        }
        if (isCommand(arguments, "ollama-status")) {
            return ollamaStatusCommand.execute();
        }
        if (isCommand(arguments, "ollama-model")) {
            return ollamaModelCommand.execute();
        }
        if (arguments.length == 2 && "ollama-prompt".equals(arguments[0])) {
            return ollamaPromptCommand.execute(arguments[1]);
        }
        if (isCommand(arguments, "conversation")) {
            return conversationCommand.execute();
        }
        if (arguments.length > 0 && "ollama-prompt".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected one quoted prompt. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "knowledge-ingest".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected one local .txt path. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "knowledge-retrieve".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected one quoted knowledge query. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "knowledge-ask".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected one quoted knowledge question. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "memory-create".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected memory-create answer-detail <concise|balanced|detailed>. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "memory-inspect".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected memory-inspect answer-detail. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "memory-edit".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected memory-edit answer-detail <concise|balanced|detailed>. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "memory-delete".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected memory-delete answer-detail. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "memory-privacy".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected memory-privacy without arguments. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "read-local-file".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected one quoted file question. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "http-get".equals(arguments[0])) {
            context.errorOutput().println(
                    "Expected one quoted web question. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "web-search".equals(arguments[0])) {
            context.errorOutput().println("Expected one quoted search question. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "agent".equals(arguments[0])) {
            context.errorOutput().println("Expected one quoted agent goal. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "tools".equals(arguments[0])) {
            context.errorOutput().println("Expected tools without arguments. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        if (arguments.length > 0 && "tool-history".equals(arguments[0])) {
            context.errorOutput().println("Expected tool-history without arguments. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }

        context.errorOutput().println(invalidArgumentsMessage(arguments));
        return KaosApplication.USAGE_ERROR;
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

    @FunctionalInterface
    interface Command {
        int execute();
    }

    @FunctionalInterface
    interface ArgumentCommand {
        int execute(String argument);
    }

    @FunctionalInterface
    interface TwoArgumentCommand {
        int execute(String firstArgument, String secondArgument);
    }
}
