package io.kaos.tool.websearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrowserSearchFallbackTest {
    private static final WebSearchRequest QUERY = new WebSearchRequest("public facts");
    private static final List<WebSearchResult.Entry> ONE_DOMAIN = List.of(
            entry("https://www.one.example/a"), entry("https://one.example/b"),
            entry("https://one.example/c"));
    private static final List<WebSearchResult.Entry> TWO_DOMAINS = List.of(
            entry("https://www.one.example/a"), entry("https://one.example/b"),
            entry("https://two.example/c"));

    @Test void configuredPolicyDistinguishesEmptySparseDomainEngineAndSufficientResults() {
        Map<String, String> previous = save();
        try {
            System.setProperty(BrowserSearchFallback.PROPERTY, "true");
            System.setProperty(BrowserSearchFallback.MIN_RESULTS_PROPERTY, "3");
            System.setProperty(BrowserSearchFallback.MIN_DOMAINS_PROPERTY, "2");
            System.setProperty(BrowserSearchFallback.MIN_ENGINES_PROPERTY, "2");
            assertReason(BrowserSearchFallback.Reason.EMPTY_RESULTS, List.of(), -1, -1, true);
            assertReason(BrowserSearchFallback.Reason.TOO_FEW_RESULTS,
                    TWO_DOMAINS.subList(0, 1), 2, 1, true);
            assertReason(BrowserSearchFallback.Reason.LOW_DOMAIN_DIVERSITY, ONE_DOMAIN, 2, 1, true);
            assertReason(BrowserSearchFallback.Reason.ENGINE_METADATA_UNAVAILABLE, TWO_DOMAINS, -1, 1, true);
            assertReason(BrowserSearchFallback.Reason.LOW_ENGINE_DIVERSITY, TWO_DOMAINS, 1, 1, true);
            var sufficient = assertReason(BrowserSearchFallback.Reason.SUFFICIENT,
                    TWO_DOMAINS, 2, 1, false);
            assertEquals(3, sufficient.results());
            assertEquals(2, sufficient.domains());
            assertEquals(2, sufficient.contributingEngines());
            assertEquals(1, sufficient.failedEngines());
        } finally { restore(previous); }
    }

    @Test void oneThresholdsPreserveZeroOnlyFallbackAndInvalidConfigurationNeverLaunchesBrowser() {
        Map<String, String> previous = save();
        try {
            System.setProperty(BrowserSearchFallback.PROPERTY, "true");
            System.setProperty(BrowserSearchFallback.MIN_RESULTS_PROPERTY, "1");
            System.setProperty(BrowserSearchFallback.MIN_DOMAINS_PROPERTY, "1");
            System.setProperty(BrowserSearchFallback.MIN_ENGINES_PROPERTY, "1");
            assertReason(BrowserSearchFallback.Reason.SUFFICIENT,
                    TWO_DOMAINS.subList(0, 1), -1, -1, false);
            System.setProperty(BrowserSearchFallback.MIN_RESULTS_PROPERTY, "0");
            assertReason(BrowserSearchFallback.Reason.INVALID_CONFIGURATION,
                    TWO_DOMAINS, 2, 0, false);
            System.setProperty(BrowserSearchFallback.PROPERTY, "false");
            assertReason(BrowserSearchFallback.Reason.DISABLED, List.of(), -1, -1, false);
        } finally { restore(previous); }
    }

    private static BrowserSearchFallback.Decision assertReason(BrowserSearchFallback.Reason reason,
            List<WebSearchResult.Entry> entries, int contributing, int failed, boolean fallback) {
        var result = new WebSearchResult(QUERY, entries,
                new WebSearchResult.EngineSignals(contributing, failed));
        var decision = BrowserSearchFallback.evaluate(result);
        assertEquals(reason, decision.reason());
        if (fallback) assertTrue(decision.fallback());
        else assertFalse(decision.fallback());
        return decision;
    }

    private static WebSearchResult.Entry entry(String url) {
        return new WebSearchResult.Entry("Title", url, "Snippet");
    }

    private static Map<String, String> save() {
        Map<String, String> previous = new HashMap<>();
        for (String property : List.of(BrowserSearchFallback.PROPERTY,
                BrowserSearchFallback.MIN_RESULTS_PROPERTY, BrowserSearchFallback.MIN_DOMAINS_PROPERTY,
                BrowserSearchFallback.MIN_ENGINES_PROPERTY)) {
            previous.put(property, System.getProperty(property));
        }
        return previous;
    }

    private static void restore(Map<String, String> previous) {
        previous.forEach((property, value) -> {
            if (value == null) System.clearProperty(property);
            else System.setProperty(property, value);
        });
    }
}
