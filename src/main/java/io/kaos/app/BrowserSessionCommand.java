package io.kaos.app;

import java.net.URI;
import java.util.Objects;

/** Controls one foreground, in-memory browser session. */
final class BrowserSessionCommand {
    private final CommandContext context;

    BrowserSessionCommand(CommandContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    int execute(String rawUrl) {
        URI uri = BrowserInspectCommand.parse(rawUrl);
        if (uri == null) {
            context.errorOutput().println(
                    "Expected one HTTP URL on 127.0.0.1 with an explicit port; no session was started.");
            return KaosApplication.USAGE_ERROR;
        }

        try {
            return BrowserSessionRuntime.run(context, uri);
        } catch (LinkageError exception) {
            context.errorOutput().println(
                    "Browser session runtime is unavailable. Run with the installed KAOS dependencies.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    static SessionInput parseInput(String rawInput) {
        String input = rawInput == null ? "" : rawInput.strip();
        return switch (input) {
            case "close" -> new SessionInput(SessionAction.CLOSE, null);
            case "status" -> new SessionInput(SessionAction.STATUS, null);
            case "back" -> new SessionInput(SessionAction.BACK, null);
            case "forward" -> new SessionInput(SessionAction.FORWARD, null);
            case "reload" -> new SessionInput(SessionAction.RELOAD, null);
            case "inspect" -> new SessionInput(SessionAction.INSPECT_CURRENT, null);
            default -> parseInspection(input);
        };
    }

    private static SessionInput parseInspection(String input) {
        if (!input.startsWith("inspect ")) {
            return new SessionInput(SessionAction.UNKNOWN, null);
        }
        URI uri = BrowserInspectCommand.parse(input.substring("inspect ".length()).strip());
        return uri == null
                ? new SessionInput(SessionAction.INVALID_URL, null)
                : new SessionInput(SessionAction.INSPECT_URL, uri);
    }

    enum SessionAction {
        CLOSE,
        STATUS,
        BACK,
        FORWARD,
        RELOAD,
        INSPECT_CURRENT,
        INSPECT_URL,
        INVALID_URL,
        UNKNOWN
    }

    record SessionInput(SessionAction action, URI uri) {
    }
}
