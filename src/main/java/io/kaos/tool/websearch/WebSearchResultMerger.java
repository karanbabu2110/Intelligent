package io.kaos.tool.websearch;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Preserves primary result order and source provenance while collapsing conservative URL matches. */
public final class WebSearchResultMerger {
    private WebSearchResultMerger() { }

    public static WebSearchResult merge(WebSearchResult primary, WebSearchResult browser) {
        if (primary == null || browser == null || !primary.request().equals(browser.request())) {
            throw new WebSearchException(WebSearchException.Reason.INVALID_RESPONSE);
        }
        var retained = new LinkedHashMap<String, WebSearchResult.Entry>();
        add(retained, primary.results());
        add(retained, browser.results());
        var merged = new WebSearchResult(primary.request(), List.copyOf(retained.values()), primary.engines());
        WebSearchToolContract.encodeResult(merged);
        return merged;
    }

    private static void add(LinkedHashMap<String, WebSearchResult.Entry> retained,
            List<WebSearchResult.Entry> entries) {
        for (var entry : entries) {
            String key = normalizedUrl(entry.url());
            var previous = retained.get(key);
            if (previous != null) {
                var sources = new LinkedHashSet<>(previous.provenance());
                sources.addAll(entry.provenance());
                retained.put(key, new WebSearchResult.Entry(previous.title(), previous.url(),
                        previous.snippet(), previous.provider(), new ArrayList<>(sources)));
            } else if (retained.size() < WebSearchResult.MAX_RESULTS) {
                retained.put(key, entry);
            }
        }
    }

    static String normalizedUrl(String url) {
        URI uri = URI.create(url);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
            port = -1;
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = cleanQuery(uri.getRawQuery());
        String authority = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        return scheme + "://" + authority + (port < 0 ? "" : ":" + port) + path
                + (query.isEmpty() ? "" : "?" + query);
    }

    private static String cleanQuery(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        var retained = new ArrayList<String>();
        for (String part : raw.split("&", -1)) {
            String key = part.split("=", 2)[0].toLowerCase(Locale.ROOT);
            if (!key.startsWith("utm_") && !List.of("fbclid", "gclid", "msclkid", "igshid",
                    "mc_cid", "mc_eid").contains(key)) retained.add(part);
        }
        return String.join("&", retained);
    }
}
