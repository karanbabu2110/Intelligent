package io.kaos.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.ToolResult;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Normalized foreground result; URLs are data and are never fetched. */
public record WebSearchResult(WebSearchRequest request, List<Entry> results) implements ToolResult<WebSearchRequest> {
    @Override public String toolName() { return WebSearchToolContract.NAME; }
    @Override public JsonNode modelContent() {
        return WebSearchToolContract.encodeResult(this);
    }

    public static final int MAX_RESULTS = 5;
    public static final int MAX_PAYLOAD_BYTES = 16_384;
    public WebSearchResult {
        Objects.requireNonNull(request, "request");
        results = List.copyOf(results);
        if (results.size() > MAX_RESULTS) {
            throw new WebSearchException(WebSearchException.Reason.RESULT_TOO_LARGE);
        }
    }
    public record Entry(String title, String url, String snippet) {
        public Entry {
            TextBounds.check(title, 256);
            TextBounds.check(url, 2048);
            TextBounds.check(snippet, 512);
            if (title.isBlank() || url.isBlank()) throw invalid();
            try {
                URI uri = URI.create(url);
                if (!("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getRawUserInfo() != null) throw invalid();
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
        }
        private static WebSearchException invalid() {
            return new WebSearchException(WebSearchException.Reason.INVALID_RESPONSE);
        }
        @Override public String toString() { return "Entry[REDACTED]"; }
    }
    @Override public String toString() { return "WebSearchResult[REDACTED]"; }
}
