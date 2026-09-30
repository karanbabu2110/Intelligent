package io.kaos.tool.httpget;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HtmlTextExtractorTest {
    @Test void extractsReadableArticleTextAndRemovesPageChrome() throws Exception {
        String html = "<html><head><title>Market title</title><style>bad()</style></head>"
                + "<body><nav>Menu</nav><script>secret()</script><article>"
                + "<h1>Markets today</h1><p>Stocks rose &amp; bonds held.</p>"
                + "<div class='advertisement'>Buy now</div><h2>Outlook</h2><p>Analysts expect caution.</p>"
                + "</article><footer>Copyright</footer></body></html>";
        String text = HtmlTextExtractor.extract(html);
        assertTrue(text.contains("Market title"));
        assertTrue(text.contains("Markets today"));
        assertTrue(text.contains("Stocks rose & bonds held."));
        assertTrue(text.contains("Outlook"));
        assertTrue(text.contains("Analysts expect caution."));
        assertFalse(text.contains("secret"));
        assertFalse(text.contains("Menu"));
        assertFalse(text.contains("Copyright"));
        assertFalse(text.contains("Buy now"));
    }

    @Test void largeRawHtmlCanYieldSmallModelTextAndRawOverflowStopsStreaming() throws Exception {
        String html = "<html><body><article><h1>Headline</h1><p>" + "x".repeat(40_000)
                + "</p></article></body></html>";
        try (var fixture = new HttpSourceFixture(html, "text/html", 200, false)) {
            HttpGetResult result = fixture.retrieve(fixture.validator().validate(
                    new HttpGetRequest("https://one.example/page")), Duration.ofSeconds(2));
            assertTrue(result.content().contains("Headline"));
            assertTrue(result.utf8ByteCount() < 64 * 1024);
            assertFalse(result.modelContent().path("content").asText().contains("<article>"));
        }
        try (var fixture = new HttpSourceFixture("x".repeat(HttpGetExecutor.MAX_RAW_RESPONSE_BYTES + 1),
                "text/html", 200, false)) {
            assertEquals(HttpGetException.Reason.TOO_LARGE, assertThrows(HttpGetException.class,
                    () -> fixture.retrieve(fixture.validator().validate(
                            new HttpGetRequest("https://one.example/page")), Duration.ofSeconds(2))).reason());
        }
    }
}
