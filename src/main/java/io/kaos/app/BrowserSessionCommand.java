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
        String raw = rawInput == null ? "" : rawInput.stripLeading();
        if ("fill".equals(raw)) {
            return new SessionInput(SessionAction.INVALID_INPUT, null, null, null);
        }
        if (raw.startsWith("fill ")) return parseFill(raw.substring("fill ".length()));
        if (raw.startsWith("record ")) {
            return switch (raw.stripTrailing()) {
                case "record on" -> new SessionInput(SessionAction.RECORD_ON, null);
                case "record off" -> new SessionInput(SessionAction.RECORD_OFF, null);
                case "record show" -> new SessionInput(SessionAction.RECORD_SHOW, null);
                case "record clear" -> new SessionInput(SessionAction.RECORD_CLEAR, null);
                default -> new SessionInput(SessionAction.INVALID_INPUT, null, null, null);
            };
        }
        String input = raw.stripTrailing();
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

    private static SessionInput parseFill(String input) {
        int separator = input.indexOf(' ');
        if (separator < 1) return new SessionInput(SessionAction.INVALID_INPUT, null, null, null);
        String selector = input.substring(0, separator);
        String value = input.substring(separator + 1);
        if (selector.codePointCount(0, selector.length()) > 256
                || value.codePointCount(0, value.length()) > 256) {
            return new SessionInput(SessionAction.INVALID_INPUT, null, null, null);
        }
        return new SessionInput(SessionAction.FILL, null, selector, value);
    }

    static boolean eligibleFillTarget(
            String tagName, String inputType, boolean visible, boolean enabled, boolean editable) {
        if (!visible || !enabled || !editable) return false;
        if ("textarea".equals(tagName)) return true;
        if (!"input".equals(tagName)) return false;
        String type = inputType == null ? "text" : inputType.toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of("text", "email", "search", "tel", "url").contains(type);
    }

    static boolean approvalGranted(String response) {
        return response != null && "approve".equals(response.strip());
    }

    enum SessionAction {
        CLOSE,
        STATUS,
        BACK,
        FORWARD,
        RELOAD,
        INSPECT_CURRENT,
        INSPECT_URL,
        FILL,
        RECORD_ON,
        RECORD_OFF,
        RECORD_SHOW,
        RECORD_CLEAR,
        INVALID_INPUT,
        INVALID_URL,
        UNKNOWN
    }

    record SessionInput(SessionAction action, URI uri, String selector, String value) {
        SessionInput(SessionAction action, URI uri) {
            this(action, uri, null, null);
        }
    }
}
