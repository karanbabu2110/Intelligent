package io.kaos.app.research;

import io.kaos.tool.browserrender.BrowserRenderException;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Exact-origin request policy for one approved research render or browser search. */
final class ResearchBrowserRequestPolicy {
    static final int MAX_REQUESTS = 32;
    static final int MAX_REDIRECTS = 3;

    private final URI selected;
    private int requests;
    private int documents;
    private int redirects;
    private URI expectedRedirect;

    ResearchBrowserRequestPolicy(URI selected) {
        this.selected = Objects.requireNonNull(selected, "selected");
    }

    URI authorize(String rawUrl, String method, String resourceType,
            boolean navigation, String redirectedFrom) {
        if (++requests > MAX_REQUESTS) fail(BrowserRenderException.Reason.REQUEST_LIMIT);
        if (!"GET".equals(method)) fail(BrowserRenderException.Reason.REQUEST_BLOCKED);
        URI request = normalize(rawUrl);
        if (!sameOrigin(request, selected)) fail(BrowserRenderException.Reason.REQUEST_BLOCKED);
        String type = resourceType == null ? "" : resourceType.toLowerCase(Locale.ROOT);
        if (navigation) {
            if (!"document".equals(type)) fail(BrowserRenderException.Reason.REQUEST_BLOCKED);
            if (redirectedFrom == null) {
                if (documents == 0) {
                    if (!request.equals(selected)) fail(BrowserRenderException.Reason.REQUEST_BLOCKED);
                } else {
                    if (!request.equals(expectedRedirect) || ++redirects > MAX_REDIRECTS) {
                        fail(redirects > MAX_REDIRECTS
                                ? BrowserRenderException.Reason.REDIRECT_LIMIT
                                : BrowserRenderException.Reason.REQUEST_BLOCKED);
                    }
                    expectedRedirect = null;
                }
            } else {
                URI previous = normalize(redirectedFrom);
                if (!sameOrigin(previous, selected) || ++redirects > MAX_REDIRECTS) {
                    fail(redirects > MAX_REDIRECTS
                            ? BrowserRenderException.Reason.REDIRECT_LIMIT
                            : BrowserRenderException.Reason.REQUEST_BLOCKED);
                }
                expectedRedirect = null;
            }
            documents++;
            return request;
        }
        if (!"script".equals(type) && !"stylesheet".equals(type)) {
            fail(BrowserRenderException.Reason.REQUEST_BLOCKED);
        }
        return request;
    }

    URI validateRedirect(URI base, String location) {
        if (location == null || location.isBlank()) fail(BrowserRenderException.Reason.REDIRECT_BLOCKED);
        URI redirect;
        try {
            redirect = normalize(base.resolve(location).toASCIIString());
        } catch (IllegalArgumentException exception) {
            throw new BrowserRenderException(BrowserRenderException.Reason.REDIRECT_BLOCKED);
        }
        if (!sameOrigin(redirect, selected)) fail(BrowserRenderException.Reason.REDIRECT_BLOCKED);
        expectedRedirect = redirect;
        return redirect;
    }

    URI followRedirect(URI base, String location) {
        if (++requests > MAX_REQUESTS) fail(BrowserRenderException.Reason.REQUEST_LIMIT);
        URI redirect = validateRedirect(base, location);
        if (++redirects > MAX_REDIRECTS) fail(BrowserRenderException.Reason.REDIRECT_LIMIT);
        expectedRedirect = null;
        return redirect;
    }

    int requestCount() {
        return requests;
    }

    void blockedRequest() {
        if (++requests > MAX_REQUESTS) fail(BrowserRenderException.Reason.REQUEST_LIMIT);
    }

    private static URI normalize(String raw) {
        try {
            return HttpGetPermissionValidator.validateSyntax(new HttpGetRequest(raw)).uri();
        } catch (RuntimeException exception) {
            throw new BrowserRenderException(BrowserRenderException.Reason.INVALID_REQUEST);
        }
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }

    private static void fail(BrowserRenderException.Reason reason) {
        throw new BrowserRenderException(reason);
    }
}
