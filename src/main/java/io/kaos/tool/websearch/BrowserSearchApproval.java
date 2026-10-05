package io.kaos.tool.websearch;

import io.kaos.tool.permission.ToolPermissionDecision;
import java.util.Objects;
import java.util.Optional;

/** Independent decision for disclosing the approved query to a browser search engine. */
public final class BrowserSearchApproval {
    public record Outcome(ToolPermissionDecision decision, Optional<Grant> grant) { }
    private final WebSearchRequest request;
    private final BrowserSearchFallback.Reason reason;
    private boolean decided;

    public BrowserSearchApproval(WebSearchRequest request, BrowserSearchFallback.Reason reason) {
        this.request = Objects.requireNonNull(request);
        this.reason = Objects.requireNonNull(reason);
        if (reason == BrowserSearchFallback.Reason.DISABLED
                || reason == BrowserSearchFallback.Reason.SUFFICIENT
                || reason == BrowserSearchFallback.Reason.INVALID_CONFIGURATION) {
            throw new IllegalArgumentException("Browser approval requires a fallback reason.");
        }
    }

    public String prompt() {
        return "Browser search fallback reason: " + reason.name() + ".\n"
                + "KAOS can send this query to Bing in an isolated Chromium session:\n\""
                + request.query() + "\"\nType 'approve' for one browser search attempt or 'deny' to skip.";
    }

    public synchronized Outcome decide(String response) {
        if (decided) throw new WebSearchException(WebSearchException.Reason.APPROVAL_REUSED);
        decided = true;
        ToolPermissionDecision decision = ToolPermissionDecision.parse(response);
        return new Outcome(decision, decision == ToolPermissionDecision.APPROVED
                ? Optional.of(new Grant(request)) : Optional.empty());
    }

    public static final class Grant {
        private WebSearchRequest request;
        private Grant(WebSearchRequest request) { this.request = request; }
        public synchronized WebSearchRequest claim() {
            if (request == null) throw new WebSearchException(WebSearchException.Reason.APPROVAL_REUSED);
            WebSearchRequest value = request;
            request = null;
            return value;
        }
        @Override public String toString() { return "BrowserSearchGrant[REDACTED]"; }
    }
}
