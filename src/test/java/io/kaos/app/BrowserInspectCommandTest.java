package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrowserInspectCommandTest {
    @Test
    void acceptsOnlyExplicitHttpLoopbackUrlsWithoutUserInfo() {
        URI accepted = BrowserInspectCommand.parse("http://127.0.0.1:8080/a?secret=value");

        assertNotNull(accepted);
        assertEquals("127.0.0.1", accepted.getHost());
        assertEquals(8080, accepted.getPort());
        assertNull(BrowserInspectCommand.parse("https://127.0.0.1:8080/"));
        assertNull(BrowserInspectCommand.parse("http://localhost:8080/"));
        assertNull(BrowserInspectCommand.parse("http://192.168.1.1:8080/"));
        assertNull(BrowserInspectCommand.parse("http://127.0.0.1/"));
        assertNull(BrowserInspectCommand.parse("http://user@127.0.0.1:8080/"));
        assertNull(BrowserInspectCommand.parse("file:///tmp/page.html"));
        assertNull(BrowserInspectCommand.parse("not a URL"));
        assertNull(BrowserInspectCommand.parse(" "));
    }

    @Test
    void permitsOnlyLoopbackGetDocumentRequests() {
        assertTrue(BrowserInspectCommand.allowRequest(
                "http://127.0.0.1:8080/page", "GET", "document"));
        assertFalse(BrowserInspectCommand.allowRequest(
                "http://example.com/page", "GET", "document"));
        assertFalse(BrowserInspectCommand.allowRequest(
                "http://127.0.0.1:8080/page", "POST", "document"));
        assertFalse(BrowserInspectCommand.allowRequest(
                "http://127.0.0.1:8080/image.png", "GET", "image"));
        assertFalse(BrowserInspectCommand.allowRequest("malformed", "GET", "document"));
    }

    @Test
    void removesQueryAndFragmentFromReportedUrl() {
        assertEquals("http://127.0.0.1:8080/private/page",
                BrowserInspectCommand.withoutQueryAndFragment(
                        "http://127.0.0.1:8080/private/page?token=secret#section"));
    }

    @Test
    void boundsVisibleTextByUnicodeCodePoints() {
        String astralCharacter = "\uD83D\uDE00";
        String value = "a".repeat(3999) + astralCharacter + "b";

        String bounded = BrowserInspectCommand.bound(value);

        assertTrue(bounded.startsWith("a".repeat(3999) + astralCharacter));
        assertTrue(bounded.endsWith("[Visible text truncated at 4000 Unicode code points.]"));
        int markerIndex = bounded.indexOf("\n[Visible text");
        assertEquals(4000, bounded.substring(0, markerIndex).codePointCount(0, markerIndex));
    }

    @Test
    void rejectsNonLoopbackUrlBeforeStartingBrowser() {
        ByteArrayOutputStream standard = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(new ApplicationConfiguration("KAOS"),
                InputStream.nullInputStream(),
                new PrintStream(standard, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));

        int result = new BrowserInspectCommand(context).execute("https://example.com/");

        assertEquals(KaosApplication.USAGE_ERROR, result);
        assertEquals("", standard.toString(StandardCharsets.UTF_8));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("no browser was started"));
    }
}
