package io.kaos.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.tool.browserrender.BrowserRenderException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ResearchBrowserRequestPolicyTest {
    @Test void permitsOnlyInitialDocumentAndSameOriginScriptOrStylesheetGets() {
        var policy = new ResearchBrowserRequestPolicy(URI.create("https://one.example/page"));

        assertEquals(URI.create("https://one.example/page"),
                policy.authorize("https://one.example/page", "GET", "document", true, null));
        assertEquals(URI.create("https://one.example/app.js"),
                policy.authorize("https://one.example/app.js", "GET", "script", false, null));
        assertEquals(URI.create("https://one.example/site.css"),
                policy.authorize("https://one.example/site.css", "GET", "stylesheet", false, null));

        assertReason(BrowserRenderException.Reason.REQUEST_BLOCKED,
                () -> policy.authorize("https://cdn.example/app.js", "GET", "script", false, null));
        assertReason(BrowserRenderException.Reason.REQUEST_BLOCKED,
                () -> policy.authorize("https://one.example/api", "GET", "fetch", false, null));
        assertReason(BrowserRenderException.Reason.REQUEST_BLOCKED,
                () -> policy.authorize("https://one.example/submit", "POST", "document", true, null));
        assertReason(BrowserRenderException.Reason.INVALID_REQUEST,
                () -> policy.authorize("http://127.0.0.1/private", "GET", "script", false, null));
    }

    @Test void permitsAtMostThreeSameOriginRedirectsAndRejectsOtherOrigins() {
        var policy = new ResearchBrowserRequestPolicy(URI.create("https://one.example/start"));
        policy.authorize("https://one.example/start", "GET", "document", true, null);
        String previous = "https://one.example/start";
        for (int index = 1; index <= 3; index++) {
            String next = "https://one.example/r" + index;
            policy.authorize(next, "GET", "document", true, previous);
            previous = next;
        }
        String finalPrevious = previous;
        assertReason(BrowserRenderException.Reason.REDIRECT_LIMIT,
                () -> policy.authorize("https://one.example/r4", "GET", "document", true, finalPrevious));
        assertReason(BrowserRenderException.Reason.REDIRECT_BLOCKED,
                () -> policy.validateRedirect(URI.create("https://one.example/start"), "https://two.example/end"));
    }

    @Test void countsBlockedRequestsAgainstTheFixedRequestLimit() {
        var policy = new ResearchBrowserRequestPolicy(URI.create("https://one.example/start"));
        for (int index = 0; index < ResearchBrowserRequestPolicy.MAX_REQUESTS; index++) {
            try {
                policy.authorize("https://two.example/" + index, "GET", "image", false, null);
            } catch (BrowserRenderException ignored) { }
        }
        assertReason(BrowserRenderException.Reason.REQUEST_LIMIT,
                () -> policy.authorize("https://one.example/app.js", "GET", "script", false, null));
        assertEquals(ResearchBrowserRequestPolicy.MAX_REQUESTS + 1, policy.requestCount());
    }

    private static void assertReason(BrowserRenderException.Reason reason, Runnable operation) {
        assertEquals(reason, assertThrows(BrowserRenderException.class, operation::run).reason());
    }
}
