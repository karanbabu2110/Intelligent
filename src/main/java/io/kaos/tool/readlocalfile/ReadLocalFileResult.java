package io.kaos.tool.readlocalfile;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.ToolResult;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One complete, bounded UTF-8 text result associated with its validated request. */
public record ReadLocalFileResult(ReadLocalFileRequest request, String content) implements ToolResult<ReadLocalFileRequest> {
    @Override public String toolName() { return ReadLocalFileToolContract.NAME; }
    @Override public JsonNode modelContent() {
        return ReadLocalFileToolContract.encodeResult(this);
    }

    public static final int MAX_CONTENT_UTF8_BYTES = 2_048;

    public ReadLocalFileResult {
        Objects.requireNonNull(request, "request");
        if (content == null) {
            throw new IllegalArgumentException("tool result content must not be null");
        }
        if (content.isBlank()) {
            throw new IllegalArgumentException("tool result content must not be blank");
        }
        if (content.codePoints().anyMatch(ReadLocalFileResult::unsafeControl)) {
            throw new IllegalArgumentException(
                    "tool result content contains an unsupported control character");
        }
        if (encodedLength(content) > MAX_CONTENT_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "tool result content must contain at most "
                            + MAX_CONTENT_UTF8_BYTES + " UTF-8 bytes");
        }
    }

    /** Returns the exact encoded size already proven to be within the result ceiling. */
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
            throw new IllegalArgumentException("tool result content must be valid UTF-8", exception);
        }
    }

    private static boolean unsafeControl(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }

    @Override
    public String toString() {
        return "ReadLocalFileResult[utf8ByteCount=" + utf8ByteCount() + "]";
    }
}
