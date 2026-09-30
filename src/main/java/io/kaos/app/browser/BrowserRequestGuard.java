package io.kaos.app.browser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Applies the request policy at Chromium's request stage, including redirect hops. */
final class BrowserRequestGuard {
    private BrowserRequestGuard() {
    }

    static void attach(
            BrowserContext context, Page page, AtomicReference<URI> selectedNavigation) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(selectedNavigation, "selectedNavigation");
        CDPSession session = context.newCDPSession(page);
        session.on("Fetch.requestPaused", event -> handleRequest(session, event, selectedNavigation));

        JsonObject requestPattern = new JsonObject();
        requestPattern.addProperty("urlPattern", "*");
        requestPattern.addProperty("requestStage", "Request");
        JsonArray patterns = new JsonArray();
        patterns.add(requestPattern);
        JsonObject options = new JsonObject();
        options.add("patterns", patterns);
        session.send("Fetch.enable", options);
    }

    private static void handleRequest(
            CDPSession session, JsonObject event, AtomicReference<URI> selectedNavigation) {
        String requestId = event.get("requestId").getAsString();
        JsonObject request = event.getAsJsonObject("request");
        String url = request.get("url").getAsString();
        String method = request.get("method").getAsString();
        String resourceType = event.get("resourceType").getAsString().toLowerCase(Locale.ROOT);

        JsonObject command = new JsonObject();
        command.addProperty("requestId", requestId);
        if (BrowserInspectCommand.allowRequest(
                url, method, resourceType, selectedNavigation.get())) {
            session.send("Fetch.continueRequest", command);
        } else {
            command.addProperty("errorReason", "BlockedByClient");
            session.send("Fetch.failRequest", command);
        }
    }
}
