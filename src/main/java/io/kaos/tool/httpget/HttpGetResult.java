package io.kaos.tool.httpget;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.ToolResult;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One complete bounded UTF-8 HTTP response associated with its approved request. */
public record HttpGetResult(HttpGetRequest request, String content, String mediaType) implements ToolResult<HttpGetRequest> {
    public RetrievalStatus status() { return RetrievalStatus.SUCCESS; }
    public int httpStatus() { return 200; }
    public String contentType() { return mediaType; }
    public String finalUrl() { return request.url(); }
    @Override public String toolName() { return HttpGetToolContract.NAME; }
    @Override public JsonNode modelContent() {
        return HttpGetToolContract.encodeResult(this);
    }

    /** Maximum text sent to the model after transport and HTML extraction. */
    public static final int MAX_MODEL_TEXT_UTF8_BYTES = 64 * 1024;
    public static final int MAX_MODEL_TEXT_CODE_POINTS = 64 * 1024;
    /** Compatibility alias for callers that use the result boundary. */
    public static final int MAX_CONTENT_UTF8_BYTES = MAX_MODEL_TEXT_UTF8_BYTES;

    public HttpGetResult {
        Objects.requireNonNull(request, "request");
        if (content == null || content.isBlank()
                || encodedLength(content) > MAX_MODEL_TEXT_UTF8_BYTES
                || content.codePoints().anyMatch(HttpGetResult::unsafeControl)) {
            throw new IllegalArgumentException("HTTP GET content is invalid.");
        }
        if (mediaType == null || mediaType.isBlank()
                || mediaType.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("HTTP GET media type is invalid.");
        }
    }

    public int utf8ByteCount() {
        return content.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int encodedLength(String content) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(content));
            return encoded.remaining();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("HTTP GET content must be valid UTF-8.", exception);
        }
    }

    private static boolean unsafeControl(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t';
    }

    @Override
    public String toString() {
        return "HttpGetResult[mediaType=" + mediaType
                + ", utf8ByteCount=" + utf8ByteCount() + "]";
    }
}
