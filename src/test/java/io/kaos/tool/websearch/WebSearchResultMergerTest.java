package io.kaos.tool.websearch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebSearchResultMergerTest {
    private final WebSearchRequest request = new WebSearchRequest("current facts");

    @Test void exactTrackingAndFragmentDuplicatesRetainBothSourcesInPrimaryOrder() {
        var primary = result(entry("First", "https://EXAMPLE.com:443/story?item=1&utm_source=news#top",
                "SEARXNG", "BING"),
                entry("Second", "https://example.com/next?item=2#section", "SEARXNG", "BRAVE"));
        var browser = result(entry("Exact match", "https://EXAMPLE.com:443/story?item=1&utm_source=news#top",
                "BROWSER_BING", "BING"),
                entry("Other title", "https://example.com/story?item=1#body",
                "BROWSER_BING", "BING"),
                entry("Another title", "https://example.com/next?item=2&fbclid=123",
                        "BROWSER_BING", "BING"),
                entry("Third", "https://example.com/third", "BROWSER_BING", "BING"));

        var merged = WebSearchResultMerger.merge(primary, browser);
        assertEquals(List.of("First", "Second", "Third"),
                merged.results().stream().map(WebSearchResult.Entry::title).toList());
        assertEquals(primary.results().getFirst().url(), merged.results().getFirst().url());
        assertEquals(List.of(new WebSearchResult.Provenance("SEARXNG", "BING"),
                new WebSearchResult.Provenance("BROWSER_BING", "BING")),
                merged.results().getFirst().provenance());
        assertEquals(2, merged.results().get(1).provenance().size());
        assertEquals("BROWSER_BING", merged.results().get(2).provider());
    }

    @Test void sameTitleAndMeaningfulQueryRemainDistinct() {
        var primary = result(entry("Same", "https://example.com/story?id=1", "SEARXNG", "BING"));
        var browser = result(entry("Same", "https://example.com/story?id=2", "BROWSER_BING", "BING"),
                entry("Same", "https://example.com/other?id=1", "BROWSER_BING", "BING"),
                entry("Same", "https://example.com/story?%75tm_source=x&id=1", "BROWSER_BING", "BING"));
        assertEquals(4, WebSearchResultMerger.merge(primary, browser).results().size());
    }

    @Test void limitKeepsFirstFiveButStillAddsProvenanceToRetainedDuplicates() {
        var primary = result(entry("One", "https://example.com/1", "SEARXNG", "BING"),
                entry("Two", "https://example.com/2", "SEARXNG", "BING"),
                entry("Three", "https://example.com/3", "SEARXNG", "BING"),
                entry("Four", "https://example.com/4", "SEARXNG", "BING"),
                entry("Five", "https://example.com/5", "SEARXNG", "BING"));
        var browser = result(entry("Six", "https://example.com/6", "BROWSER_BING", "BING"),
                entry("Two again", "https://example.com/2#fragment", "BROWSER_BING", "BING"));
        var merged = WebSearchResultMerger.merge(primary, browser);
        assertEquals(5, merged.results().size());
        assertEquals(2, merged.results().get(1).provenance().size());
        assertEquals("https://example.com/5", merged.results().getLast().url());
    }

    @Test void rejectsDifferentRequests() {
        var other = new WebSearchResult(new WebSearchRequest("different"), List.of());
        assertEquals(WebSearchException.Reason.INVALID_RESPONSE,
                assertThrows(WebSearchException.class,
                        () -> WebSearchResultMerger.merge(result(), other)).reason());
    }

    private WebSearchResult result(WebSearchResult.Entry... entries) {
        return new WebSearchResult(request, List.of(entries));
    }
    private static WebSearchResult.Entry entry(String title, String url, String provider, String engine) {
        return new WebSearchResult.Entry(title, url, "snippet", provider,
                List.of(new WebSearchResult.Provenance(provider, engine)));
    }
}
