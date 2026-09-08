package io.kaos.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReadLocalFileResultTest {
    private static final ReadLocalFileRequest REQUEST =
            new ReadLocalFileRequest("src/Private.java");

    @Test
    void preservesCompleteTextAndReportsItsExactUtf8Size() {
        String content = "  class Café {\n\t// 🌍\n}\n  ";

        ReadLocalFileResult result = new ReadLocalFileResult(REQUEST, content);

        assertEquals(REQUEST, result.request());
        assertEquals(content, result.content());
        assertEquals(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                result.utf8ByteCount());
    }

    @Test
    void acceptsTheExactByteLimitForAsciiAndMultiByteText() {
        ReadLocalFileResult ascii = new ReadLocalFileResult(
                REQUEST, "a".repeat(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES));
        ReadLocalFileResult unicode = new ReadLocalFileResult(
                REQUEST, "🌍".repeat(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES / 4));

        assertEquals(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES, ascii.utf8ByteCount());
        assertEquals(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES, unicode.utf8ByteCount());
    }

    @Test
    void rejectsNullBlankOversizedUnsafeAndMalformedText() {
        assertThrows(NullPointerException.class, () -> new ReadLocalFileResult(null, "safe"));
        assertThrows(IllegalArgumentException.class, () -> new ReadLocalFileResult(REQUEST, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadLocalFileResult(REQUEST, " \n\t"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadLocalFileResult(
                        REQUEST, "a".repeat(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadLocalFileResult(REQUEST, "safe\u0000private"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadLocalFileResult(REQUEST, "unpaired \uD800"));
    }

    @Test
    void stringRepresentationDoesNotRevealPathOrContent() {
        String privateContent = "private customer content";
        ReadLocalFileResult result = new ReadLocalFileResult(REQUEST, privateContent);

        assertFalse(result.toString().contains(REQUEST.path()));
        assertFalse(result.toString().contains(privateContent));
        assertEquals("ReadLocalFileResult[utf8ByteCount=24]", result.toString());
    }
}
