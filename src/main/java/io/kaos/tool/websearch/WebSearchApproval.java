package io.kaos.tool.websearch;

import java.util.Objects;
import java.util.Optional;

/** One decision and one consumable grant for the exact displayed query. */
public final class WebSearchApproval {
    public enum Status { APPROVED, DENIED, CANCELLED, INVALID_RESPONSE, END_OF_INPUT }
    public record Outcome(Status status, Optional<Grant> grant) { }
    private final WebSearchRequest request;
    private boolean decided;
    public WebSearchApproval(WebSearchRequest request) {
        this.request = Objects.requireNonNull(request);
    }
    public String prompt() {
        return "Tool: web_search\nKAOS wants to search the public internet.\n"
                + "Search query:\n\"" + request.query() + "\"\n"
                + "Search service: configured self-hosted SearXNG\n"
                + "SearXNG may forward this query to configured external search engines.\n"
                + "Type 'approve' to authorize one search attempt or 'deny' to cancel.";
    }
    public synchronized Outcome decide(String response) {
        if (decided) throw new WebSearchException(WebSearchException.Reason.APPROVAL_REUSED);
        decided = true;
        Status status = Thread.currentThread().isInterrupted() ? Status.CANCELLED
                : response == null ? Status.END_OF_INPUT
                : switch (response.strip()) {
                    case "approve" -> Status.APPROVED;
                    case "deny" -> Status.DENIED;
                    default -> Status.INVALID_RESPONSE;
                };
        return new Outcome(status, status == Status.APPROVED
                ? Optional.of(new Grant(request)) : Optional.empty());
    }
    public static final class Grant {
        private WebSearchRequest request;
        private Grant(WebSearchRequest request) { this.request = request; }
        synchronized WebSearchRequest claim() {
            if (request == null) throw new WebSearchException(WebSearchException.Reason.APPROVAL_REUSED);
            WebSearchRequest value = request;
            request = null;
            return value;
        }
        @Override public String toString() { return "WebSearchGrant[REDACTED]"; }
    }
    @Override public String toString() { return "WebSearchApproval[REDACTED]"; }
}
