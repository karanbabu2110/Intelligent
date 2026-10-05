package io.kaos.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.tool.browserrender.BrowserRenderException;
import io.kaos.app.browsersearch.BingBrowserSearchProvider;
import io.kaos.app.browsersearch.BrowserSearchProvider;
import com.microsoft.playwright.Page;
import io.kaos.tool.browserrender.BrowserResource;
import io.kaos.tool.browserrender.BrowserRenderedResult;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ResearchBrowserRendererTest {
    @Test void extractsBoundedStructuredBrowserSearchResultsWithoutOpeningLinks() {
        var requested = new CopyOnWriteArrayList<String>();
        var renderer = new ResearchBrowserRenderer((uri, timeout, maxBytes) -> {
            requested.add(uri.getPath());
            assertEquals("www.bing.com", uri.getHost());
            return resource(uri, 200, "text/html", "", """
                    <html><body><ol id="b_results">
                    <li class="b_algo"><h2><a href="https://example.com/one">First result</a></h2>
                    <div class="b_caption"><p>Useful snippet</p></div></li>
                    <li class="b_algo"><h2><a href="javascript:alert(1)">Bad link</a></h2></li>
                    </ol></body></html>
                    """);
        });
        var result = renderer.search(new WebSearchRequest("bounded facts"), new BingBrowserSearchProvider());
        assertEquals(1, result.results().size());
        assertEquals("First result", result.results().getFirst().title());
        assertEquals("https://example.com/one", result.results().getFirst().url());
        assertEquals("Useful snippet", result.results().getFirst().snippet());
        assertEquals("BROWSER_BING", result.results().getFirst().provider());
        assertEquals(List.of("/search"), requested);
    }

    @Test void classifiesBrowserSearchFailuresAndReleasesContextAfterFailure() {
        var provider = new BingBrowserSearchProvider();
        var request = new WebSearchRequest("bounded facts");
        assertTrue(provider.searchUri(request).getRawQuery().contains("bounded+facts"));
        var captcha = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 200, "text/html", "", "<body><form action='/captcha'></form></body>"));
        assertEquals(BrowserRenderException.Reason.CAPTCHA,
                assertThrows(BrowserRenderException.class, () -> captcha.search(request, provider)).reason());

        var layout = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 200, "text/html", "", "<body>Changed results layout</body>"));
        assertEquals(BrowserRenderException.Reason.UNSUPPORTED_LAYOUT,
                assertThrows(BrowserRenderException.class, () -> layout.search(request, provider)).reason());

        var denied = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 403, "text/html", "", "denied"));
        assertEquals(BrowserRenderException.Reason.ACCESS_DENIED,
                assertThrows(BrowserRenderException.class, () -> denied.search(request, provider)).reason());
        var limited = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 429, "text/html", "", "slow down"));
        assertEquals(BrowserRenderException.Reason.RATE_LIMITED,
                assertThrows(BrowserRenderException.class, () -> limited.search(request, provider)).reason());
        var timeout = new ResearchBrowserRenderer((uri, boundedTime, maxBytes) -> {
            throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
        });
        assertEquals(BrowserRenderException.Reason.TIMEOUT,
                assertThrows(BrowserRenderException.class, () -> timeout.search(request, provider)).reason());

        AtomicBoolean first = new AtomicBoolean(true);
        var reusable = new ResearchBrowserRenderer((uri, boundedTime, maxBytes) ->
                resource(uri, 200, "text/html", "", first.getAndSet(false)
                        ? "<body>Unexpected layout</body>"
                        : "<body><li class='b_algo'><h2><a href='https://example.com/ok'>OK</a></h2></li></body>"));
        assertEquals(BrowserRenderException.Reason.UNSUPPORTED_LAYOUT,
                assertThrows(BrowserRenderException.class, () -> reusable.search(request, provider)).reason());
        assertEquals("OK", reusable.search(request, provider).results().getFirst().title());
    }

    @Test void supportsAnotherProviderWithoutChangingTheChromiumRunner() {
        var provider = new BrowserSearchProvider() {
            @Override public String provenance() { return "BROWSER_FIXTURE"; }
            @Override public URI searchUri(WebSearchRequest request) {
                return URI.create("https://search.example/results?q=fixture");
            }
            @Override public WebSearchResult extract(Page page, WebSearchRequest request) {
                return new WebSearchResult(request, List.of(new WebSearchResult.Entry(
                        page.locator("h2").innerText(), "https://example.com/fact", "fixture snippet", provenance())));
            }
        };
        var renderer = new ResearchBrowserRenderer((uri, timeout, maxBytes) -> {
            assertEquals("search.example", uri.getHost());
            return resource(uri, 200, "text/html", "", "<body><h2>Fixture fact</h2></body>");
        });
        assertEquals("BROWSER_FIXTURE", renderer.search(new WebSearchRequest("fixture"), provider)
                .results().getFirst().provider());
    }
    @Test void rendersJavaScriptWithOnlyBoundedSameOriginDocumentScriptsAndStyles() {
        var requested = new CopyOnWriteArrayList<String>();
        var renderer = new ResearchBrowserRenderer((uri, timeout, maxBytes) -> {
            requested.add(uri.getPath());
            assertTrue(timeout.compareTo(ResearchBrowserRenderer.MAX_RESOURCE_TIME) <= 0);
            assertTrue(maxBytes <= ResearchBrowserRenderer.MAX_RESOURCE_BYTES);
            return switch (uri.getPath()) {
                case "/page" -> resource(uri, 200, "text/html", "", """
                        <html><head><title>Rendered publisher title</title>
                        <meta name="description" content="Publisher summary">
                        <link rel="stylesheet" href="/site.css"></head><body>
                        <p>initial</p><img src="/image.png"><iframe src="https://other.example/frame"></iframe>
                        <script>fetch('/api'); const i=new Image(); i.src='/tracking';
                        try { new WebSocket('wss://other.example/socket'); } catch (ignored) {}
                        localStorage.setItem('count', String(Number(localStorage.getItem('count') || '0') + 1));</script>
                        <script src="/app.js"></script>
                        </body></html>
                        """);
                case "/site.css" -> resource(uri, 200, "text/css", "", "p { display:block }");
                case "/app.js" -> resource(uri, 200, "application/javascript", "",
                        "document.body.insertAdjacentHTML('beforeend','<p>rendered storage=' + localStorage.getItem('count') + '</p>');");
                default -> throw new AssertionError("Blocked resource reached fetcher: " + uri);
            };
        });

        BrowserRenderedResult firstResult = renderer.render("https://one.example/page");
        String first = firstResult.content();
        String second = renderer.render("https://one.example/page").content();

        assertTrue(first.contains("rendered storage=1"), first);
        assertEquals("Rendered publisher title", firstResult.title());
        assertEquals("Publisher summary", firstResult.description());
        assertEquals("Publisher summary", firstResult.modelContent().path("description").asText());
        assertTrue(second.contains("rendered storage=1"), second);
        assertEquals(2, requested.stream().filter("/page"::equals).count());
        assertEquals(2, requested.stream().filter("/app.js"::equals).count());
        assertEquals(2, requested.stream().filter("/site.css"::equals).count());
        assertFalse(requested.contains("/api"));
        assertFalse(requested.contains("/image.png"));
        assertFalse(requested.contains("/tracking"));
    }

    @Test void validatesDestinationBeforeOpeningChromiumAndBoundsMetadata() {
        AtomicBoolean fetched = new AtomicBoolean();
        var renderer = new ResearchBrowserRenderer(new ResearchBrowserRenderer.ResourceFetcher() {
            @Override public BrowserResource fetch(URI uri, java.time.Duration timeout, int maxBytes) {
                fetched.set(true);
                throw new AssertionError("Unsafe destination was fetched");
            }
            @Override public void validateDestination(URI uri, java.time.Duration timeout) {
                assertEquals("one.example", uri.getHost());
                assertTrue(timeout.compareTo(ResearchBrowserRenderer.MAX_TOTAL_RUNTIME) <= 0);
                throw new BrowserRenderException(BrowserRenderException.Reason.NON_PUBLIC_DESTINATION);
            }
        });
        assertEquals(BrowserRenderException.Reason.NON_PUBLIC_DESTINATION,
                assertThrows(BrowserRenderException.class,
                        () -> renderer.render("https://one.example/page")).reason());
        assertFalse(fetched.get());

        var request = new io.kaos.tool.httpget.HttpGetRequest("https://one.example/page");
        assertThrows(IllegalArgumentException.class, () -> new BrowserRenderedResult(request,
                "content", "x".repeat(BrowserRenderedResult.MAX_TITLE_CODE_POINTS + 1), ""));
        assertThrows(IllegalArgumentException.class, () -> new BrowserRenderedResult(request,
                "content", "title", "x".repeat(BrowserRenderedResult.MAX_DESCRIPTION_CODE_POINTS + 1)));
    }

    @Test void followsBoundedSameOriginRedirectButRejectsCrossOriginRedirect() {
        var requested = new CopyOnWriteArrayList<String>();
        var sameOrigin = new ResearchBrowserRenderer((uri, timeout, maxBytes) -> {
            requested.add(uri.toASCIIString());
            return "/start".equals(uri.getPath())
                    ? resource(uri, 302, "text/html", "/final", "")
                    : resource(uri, 200, "text/html", "", "<body>redirected evidence</body>");
        });
        assertTrue(sameOrigin.render("https://one.example/start").content().contains("redirected evidence"));
        assertEquals(List.of("https://one.example/start", "https://one.example/final"), requested);

        var crossOrigin = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 302, "text/html", "https://two.example/final", ""));
        assertEquals(BrowserRenderException.Reason.REDIRECT_BLOCKED,
                assertThrows(BrowserRenderException.class,
                        () -> crossOrigin.render("https://one.example/start")).reason());
    }

    @Test void enforcesRawSizeTextSizeTimeoutAndCleansUpAfterFailure() {
        var oversized = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                new BrowserResource(uri, 200, "text/html", "", new byte[maxBytes + 1]));
        assertEquals(BrowserRenderException.Reason.TOO_LARGE,
                assertThrows(BrowserRenderException.class,
                        () -> oversized.render("https://one.example/page")).reason());

        var timeout = new ResearchBrowserRenderer((uri, boundedTime, maxBytes) -> {
            assertTrue(boundedTime.compareTo(ResearchBrowserRenderer.MAX_RESOURCE_TIME) <= 0);
            throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
        });
        assertEquals(BrowserRenderException.Reason.TIMEOUT,
                assertThrows(BrowserRenderException.class,
                        () -> timeout.render("https://one.example/page")).reason());

        AtomicBoolean fail = new AtomicBoolean(true);
        var reusable = new ResearchBrowserRenderer((uri, boundedTime, maxBytes) -> {
            if (fail.getAndSet(false)) throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
            return resource(uri, 200, "text/html", "", "<body>clean second context</body>");
        });
        assertEquals(BrowserRenderException.Reason.TIMEOUT,
                assertThrows(BrowserRenderException.class,
                        () -> reusable.render("https://one.example/page")).reason());
        assertTrue(reusable.render("https://one.example/page").content().contains("clean second context"));

        String largeText = "x".repeat(ResearchBrowserRenderer.MAX_RESOURCE_BYTES - 128);
        var boundedText = new ResearchBrowserRenderer((uri, boundedTime, maxBytes) ->
                resource(uri, 200, "text/html", "", "<body>" + largeText + "</body>"));
        int bytes = boundedText.render("https://one.example/page").content()
                .getBytes(StandardCharsets.UTF_8).length;
        assertEquals(64 * 1024, bytes);
    }

    @Test void rejectsHttpErrorsAndUnsupportedDocumentContent() {
        var error = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 403, "text/html", "", "denied"));
        assertEquals(BrowserRenderException.Reason.HTTP_ERROR,
                assertThrows(BrowserRenderException.class,
                        () -> error.render("https://one.example/page")).reason());

        var binary = new ResearchBrowserRenderer((uri, timeout, maxBytes) ->
                resource(uri, 200, "application/octet-stream", "", "binary"));
        assertEquals(BrowserRenderException.Reason.UNSUPPORTED_CONTENT,
                assertThrows(BrowserRenderException.class,
                        () -> binary.render("https://one.example/page")).reason());
    }

    private static BrowserResource resource(URI uri, int status, String type, String location, String body) {
        return new BrowserResource(uri, status, type, location, body.getBytes(StandardCharsets.UTF_8));
    }
}
