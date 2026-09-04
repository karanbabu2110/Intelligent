package io.kaos.app;

import java.util.Objects;

/**
 * Parses the public command-line shape and dispatches one application command.
 */
final class CommandRouter {
    private final CommandContext context;
    private final ArgumentCommand knowledgeIngestCommand;
    private final ArgumentCommand knowledgeRetrieveCommand;
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
        this.context = Objects.requireNonNull(context, "context");
        this.knowledgeIngestCommand = Objects.requireNonNull(
                knowledgeIngestCommand, "knowledgeIngestCommand");
        this.knowledgeRetrieveCommand = Objects.requireNonNull(
                knowledgeRetrieveCommand, "knowledgeRetrieveCommand");
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
        if (arguments.length == 2 && "knowledge-ingest".equals(arguments[0])) {
            return knowledgeIngestCommand.execute(arguments[1]);
        }
        if (arguments.length == 2 && "knowledge-retrieve".equals(arguments[0])) {
            return knowledgeRetrieveCommand.execute(arguments[1]);
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
}
