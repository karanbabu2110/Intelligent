package io.kaos.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.ToolResult;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Normalized foreground result; URLs are data and are never fetched. */
public record WebSearchResult(WebSearchRequest request, List<Entry> results, EngineSignals engines)
        implements ToolResult<WebSearchRequest> {
    @Override public String toolName() { return WebSearchToolContract.NAME; }
    @Override public JsonNode modelContent() {
        return WebSearchToolContract.encodeResult(this);
    }

    public static final int MAX_RESULTS = 5;
    public static final int MAX_PAYLOAD_BYTES = 16_384;
    public WebSearchResult(WebSearchRequest request, List<Entry> results) {
        this(request, results, EngineSignals.UNKNOWN);
    }
    public WebSearchResult {
        Objects.requireNonNull(request, "request");
        results = List.copyOf(results);
        Objects.requireNonNull(engines, "engines");
        if (results.size() > MAX_RESULTS) {
            throw new WebSearchException(WebSearchException.Reason.RESULT_TOO_LARGE);
        }
    }
    /** Counts only; raw upstream engine names and failure messages never enter model content. */
    public record EngineSignals(int contributing, int failed) {
        public static final EngineSignals UNKNOWN = new EngineSignals(-1, -1);
        public EngineSignals {
            if (contributing < -1 || contributing > 32 || failed < -1 || failed > 32) {
                throw new IllegalArgumentException("Engine counts must be bounded.");
            }
        }
    }
    public record Provenance(String provider, String engine) {
        public Provenance {
            if (!validProvider(provider) || engine == null
                    || !engine.matches("[A-Z][A-Z0-9_]{0,31}")) {
                throw new WebSearchException(WebSearchException.Reason.INVALID_RESPONSE);
            }
        }
    }
    public record Entry(String title, String url, String snippet, String provider,
            List<Provenance> provenance) {
        public Entry(String title, String url, String snippet) {
            this(title, url, snippet, "SEARXNG");
        }
        public Entry(String title, String url, String snippet, String provider) {
            this(title, url, snippet, provider, List.of(new Provenance(provider,
                    "SEARXNG".equals(provider) ? "UNKNOWN"
                            : provider != null && provider.startsWith("BROWSER_")
                                    ? provider.substring("BROWSER_".length()) : "UNKNOWN")));
        }
        public Entry {
            TextBounds.check(title, 256);
            TextBounds.check(url, 2048);
            TextBounds.check(snippet, 512);
            if (!validProvider(provider) || provenance == null || provenance.isEmpty()
                    || provenance.size() > 16 || provenance.getFirst() == null
                    || !provider.equals(provenance.getFirst().provider())
                    || new LinkedHashSet<>(provenance).size() != provenance.size()) throw invalid();
            provenance = List.copyOf(provenance);
            if (title.isBlank() || url.isBlank()) throw invalid();
            try {
                URI uri = URI.create(url);
                if (!("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getRawUserInfo() != null
                        || HttpGetPermissionValidator.hasCredentialQuery(uri)) throw invalid();
            } catch (IllegalArgumentException exception) {
                throw invalid();
            }
        }
        private static WebSearchException invalid() {
            return new WebSearchException(WebSearchException.Reason.INVALID_RESPONSE);
        }
        @Override public String toString() { return "Entry[REDACTED]"; }
    }
    private static boolean validProvider(String provider) {
        return "SEARXNG".equals(provider)
                || (provider != null && provider.matches("BROWSER_[A-Z][A-Z0-9_]{0,31}"));
    }
    @Override public String toString() { return "WebSearchResult[REDACTED]"; }
}
