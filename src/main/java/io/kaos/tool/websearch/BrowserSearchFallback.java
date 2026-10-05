package io.kaos.tool.websearch;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Deterministic, content-free quality decision for the optional browser search. */
public final class BrowserSearchFallback {
    public static final String PROPERTY = "kaos.web-search.browser-fallback-enabled";
    public static final String ENVIRONMENT = "KAOS_WEB_SEARCH_BROWSER_FALLBACK_ENABLED";
    public static final String MIN_RESULTS_PROPERTY = "kaos.web-search.browser-min-results";
    public static final String MIN_RESULTS_ENVIRONMENT = "KAOS_WEB_SEARCH_BROWSER_MIN_RESULTS";
    public static final String MIN_DOMAINS_PROPERTY = "kaos.web-search.browser-min-domains";
    public static final String MIN_DOMAINS_ENVIRONMENT = "KAOS_WEB_SEARCH_BROWSER_MIN_DOMAINS";
    public static final String MIN_ENGINES_PROPERTY = "kaos.web-search.browser-min-engines";
    public static final String MIN_ENGINES_ENVIRONMENT = "KAOS_WEB_SEARCH_BROWSER_MIN_ENGINES";

    public enum Reason {
        DISABLED, INVALID_CONFIGURATION, EMPTY_RESULTS, TOO_FEW_RESULTS,
        LOW_DOMAIN_DIVERSITY, LOW_ENGINE_DIVERSITY, ENGINE_METADATA_UNAVAILABLE, SUFFICIENT
    }

    public record Decision(boolean fallback, Reason reason, int results, int domains,
            int contributingEngines, int failedEngines) { }

    private BrowserSearchFallback() { }

    public static Decision evaluate(WebSearchResult primary) {
        Objects.requireNonNull(primary, "primary");
        int results = primary.results().size();
        int domains = uniqueHosts(primary);
        int contributing = primary.engines().contributing();
        int failed = primary.engines().failed();
        try {
            String enabled = setting(PROPERTY, ENVIRONMENT);
            if (enabled == null || "false".equalsIgnoreCase(enabled)) {
                return new Decision(false, Reason.DISABLED, results, domains, contributing, failed);
            }
            if (!"true".equalsIgnoreCase(enabled)) {
                return new Decision(false, Reason.INVALID_CONFIGURATION, results, domains, contributing, failed);
            }
            int minimumResults = threshold(MIN_RESULTS_PROPERTY, MIN_RESULTS_ENVIRONMENT, 5);
            int minimumDomains = threshold(MIN_DOMAINS_PROPERTY, MIN_DOMAINS_ENVIRONMENT, 5);
            int minimumEngines = threshold(MIN_ENGINES_PROPERTY, MIN_ENGINES_ENVIRONMENT, 32);
            Reason reason;
            if (results == 0) reason = Reason.EMPTY_RESULTS;
            else if (results < minimumResults) reason = Reason.TOO_FEW_RESULTS;
            else if (domains < minimumDomains) reason = Reason.LOW_DOMAIN_DIVERSITY;
            else if (minimumEngines > 1 && contributing < 0) reason = Reason.ENGINE_METADATA_UNAVAILABLE;
            else if (contributing >= 0 && contributing < minimumEngines) reason = Reason.LOW_ENGINE_DIVERSITY;
            else reason = Reason.SUFFICIENT;
            return new Decision(reason != Reason.SUFFICIENT, reason,
                    results, domains, contributing, failed);
        } catch (IllegalArgumentException | SecurityException exception) {
            return new Decision(false, Reason.INVALID_CONFIGURATION, results, domains, contributing, failed);
        }
    }

    private static int threshold(String property, String environment, int maximum) {
        String value = setting(property, environment);
        if (value == null) return 1;
        int parsed = Integer.parseInt(value);
        if (parsed < 1 || parsed > maximum) throw new IllegalArgumentException("Invalid search threshold.");
        return parsed;
    }

    private static String setting(String property, String environment) {
        String value = System.getProperty(property);
        return value != null ? value : System.getenv(environment);
    }

    private static int uniqueHosts(WebSearchResult result) {
        Set<String> hosts = new HashSet<>();
        for (var entry : result.results()) {
            String host = URI.create(entry.url()).getHost().toLowerCase(Locale.ROOT);
            hosts.add(host.startsWith("www.") ? host.substring(4) : host);
        }
        return hosts.size();
    }
}
