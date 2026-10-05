package io.kaos.app.browsersearch;

import com.microsoft.playwright.Page;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import java.net.URI;

/** One public engine's query URL and bounded rendered-result extraction. */
public interface BrowserSearchProvider {
    String provenance();
    URI searchUri(WebSearchRequest request);
    WebSearchResult extract(Page page, WebSearchRequest request);
}
