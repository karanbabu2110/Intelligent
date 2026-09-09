package io.kaos.tool.websearch;

/** Fixed, content-free search failures; never retains a transport cause or response. */
public final class WebSearchException extends RuntimeException {
    public enum Reason {
        INVALID_REQUEST, SEARCH_SERVICE_NOT_CONFIGURED, INVALID_CONFIGURATION,
        SEARCH_SERVICE_UNAVAILABLE, SEARCH_TIMEOUT, INVALID_RESPONSE,
        RESULT_TOO_LARGE, CANCELLED, APPROVAL_REUSED
    }
    private final Reason reason;
    public WebSearchException(Reason reason) {
        super(reason.name(), null, false, false);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
