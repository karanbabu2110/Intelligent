package io.kaos.tool.websearch;

/** Optional, explicit browser search policy. The first slice handles empty primary results only. */
public final class BrowserSearchFallback {
    public static final String PROPERTY = "kaos.web-search.browser-fallback-enabled";
    public static final String ENVIRONMENT = "KAOS_WEB_SEARCH_BROWSER_FALLBACK_ENABLED";

    private BrowserSearchFallback() { }

    public static boolean enabled() {
        String property = System.getProperty(PROPERTY);
        String value = property != null ? property : System.getenv(ENVIRONMENT);
        return "true".equalsIgnoreCase(value);
    }

    public static boolean shouldSearch(WebSearchResult primary) {
        return enabled() && primary.results().isEmpty();
    }
}
