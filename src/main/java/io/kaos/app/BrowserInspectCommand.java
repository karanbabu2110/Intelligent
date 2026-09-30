package io.kaos.app;

import java.net.URI;
import java.util.Objects;

/** Inspects one user-selected loopback page without persistent browser state or page actions. */
final class BrowserInspectCommand {
    private static final int MAX_TEXT_CODE_POINTS = 4000;
    private final CommandContext context;

    BrowserInspectCommand(CommandContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    int execute(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null) {
            context.errorOutput().println(
                    "Expected one HTTP URL on 127.0.0.1 with an explicit port; no browser was started.");
            return KaosApplication.USAGE_ERROR;
        }

        try {
            return BrowserInspectRuntime.inspect(context, uri);
        } catch (LinkageError exception) {
            context.errorOutput().println(
                    "Browser inspection runtime is unavailable. Run with the installed KAOS dependencies.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    static boolean allowRequest(String rawUrl, String method, String resourceType) {
        try {
            URI request = URI.create(rawUrl);
            return "http".equalsIgnoreCase(request.getScheme())
                    && "127.0.0.1".equals(request.getHost())
                    && "GET".equals(method)
                    && "document".equals(resourceType);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        try {
            URI uri = new URI(rawUrl).normalize();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                    || !"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getUserInfo() != null) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException | java.net.URISyntaxException exception) {
            return null;
        }
    }

    static String withoutQueryAndFragment(String value) {
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toASCIIString();
        } catch (java.net.URISyntaxException | IllegalArgumentException exception) {
            return "127.0.0.1";
        }
    }

    static String bound(String value) {
        if (value == null) return "";
        int count = value.codePointCount(0, value.length());
        if (count <= MAX_TEXT_CODE_POINTS) return value;
        int end = value.offsetByCodePoints(0, MAX_TEXT_CODE_POINTS);
        return value.substring(0, end) + "\n[Visible text truncated at "
                + MAX_TEXT_CODE_POINTS + " Unicode code points.]";
    }

}
