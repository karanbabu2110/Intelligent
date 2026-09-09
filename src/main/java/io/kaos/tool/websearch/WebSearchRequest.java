package io.kaos.tool.websearch;

/** Exact model-selected query; no endpoint, policy, or execution authority. */
public record WebSearchRequest(String query) {
    public static final int MAX_QUERY_CODE_POINTS = 400;
    public WebSearchRequest {
        if (query == null || query.codePoints().allMatch(
                point -> Character.isWhitespace(point) || Character.isSpaceChar(point))) throw invalid();
        TextBounds.check(query, MAX_QUERY_CODE_POINTS);
        // SearXNG query syntax can select engines/languages or redirect via bangs.
        // Keep those controls with the service administrator.
        if (query.contains("!") || query.matches("(?sU).*(?:^|\\s):\\S+.*")) throw invalid();
    }
    private static WebSearchException invalid() {
        return new WebSearchException(WebSearchException.Reason.INVALID_REQUEST);
    }
    @Override public String toString() { return "WebSearchRequest[query=REDACTED]"; }
}
