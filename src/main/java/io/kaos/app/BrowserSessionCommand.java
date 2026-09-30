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
}
