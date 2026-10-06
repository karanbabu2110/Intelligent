package io.kaos.app.browsersearch;

import com.microsoft.playwright.Page;
import io.kaos.tool.browserrender.BrowserRenderException;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Public Bing results page; links are extracted as data and never opened. */
public final class BingBrowserSearchProvider implements BrowserSearchProvider {
    public static final String PROVENANCE = "BROWSER_BING";

    @Override public String provenance() { return PROVENANCE; }

    @Override public URI searchUri(WebSearchRequest request) {
        return URI.create("https://www.bing.com/search?q="
                + URLEncoder.encode(request.query(), StandardCharsets.UTF_8));
    }

    @Override public WebSearchResult extract(Page page, WebSearchRequest request) {
        if (page.locator("#b_captcha, form[action*='captcha'], input[name*='captcha']").count() > 0) {
            throw new BrowserRenderException(BrowserRenderException.Reason.CAPTCHA);
        }
        if (page.locator("#bnp_ttc_div:visible, #consent-page:visible, "
                + "form[action*='consent']:visible, form[action*='privacy']:visible").count() > 0) {
            throw new BrowserRenderException(BrowserRenderException.Reason.CONSENT_INTERSTITIAL);
        }
        var cards = page.locator("li.b_algo");
        if (cards.count() == 0) {
            throw new BrowserRenderException(BrowserRenderException.Reason.UNSUPPORTED_LAYOUT);
        }
        var entries = new ArrayList<WebSearchResult.Entry>();
        int count = Math.min(cards.count(), WebSearchResult.MAX_RESULTS);
        for (int index = 0; index < count; index++) {
            var card = cards.nth(index);
            var links = card.locator("h2 a");
            if (links.count() != 1) continue;
            String url = links.first().getAttribute("href");
            String title = boundedField(links.first().innerText(), 256);
            var snippets = card.locator(".b_caption p");
            String snippet = snippets.count() == 0 ? "" : boundedField(snippets.first().innerText(), 512);
            try {
                entries.add(new WebSearchResult.Entry(title, url, snippet, provenance()));
            } catch (RuntimeException ignored) {
                // A malformed result link is untrusted data, not a navigation target.
            }
        }
        if (entries.isEmpty()) {
            throw new BrowserRenderException(BrowserRenderException.Reason.UNSUPPORTED_LAYOUT);
        }
        return new WebSearchResult(request, entries);
    }

    private static String boundedField(String raw, int limit) {
        if (raw == null) return "";
        String value = raw.replaceAll("[\\r\\n\\t]+", " ").strip();
        return value.codePointCount(0, value.length()) <= limit ? value : "";
    }
}
