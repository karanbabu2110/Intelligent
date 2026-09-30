package io.kaos.app;

import java.net.URI;
import java.util.Objects;

/** Inspects one user-selected loopback page without persistent browser state or page actions. */
final class BrowserInspectCommand {
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

    static boolean allowRequest(
            String rawUrl, String method, String resourceType, URI selectedNavigation) {
        try {
            URI request = URI.create(rawUrl);
            return "http".equalsIgnoreCase(request.getScheme())
                    && "127.0.0.1".equals(request.getHost())
                    && "GET".equals(method)
                    && "document".equals(resourceType)
                    && sameOrigin(request, selectedNavigation);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean sameOrigin(URI first, URI second) {
        if (second == null || second.getHost() == null || second.getUserInfo() != null) return false;
        return "http".equalsIgnoreCase(first.getScheme())
                && "http".equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 80;
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

}
