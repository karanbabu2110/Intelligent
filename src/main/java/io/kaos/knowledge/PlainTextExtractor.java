package io.kaos.knowledge;

import io.kaos.knowledge.TextExtractionException.Reason;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Extracts exact bounded Unicode text from one admitted UTF-8 plain-text document. */
public final class PlainTextExtractor {
    public ExtractedText extract(IngestedDocument document) throws TextExtractionException {
        Objects.requireNonNull(document, "document");
        if (!TextDocumentIngestor.MEDIA_TYPE.equals(document.mediaType())) {
            throw failure(Reason.UNSUPPORTED_MEDIA_TYPE);
        }
        if (document.byteCount() == 0) {
            throw failure(Reason.INVALID_TEXT);
        }
        if (document.byteCount() > TextDocumentIngestor.MAX_DOCUMENT_BYTES) {
            throw failure(Reason.TOO_LARGE);
        }

        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(document.content()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw failure(Reason.INVALID_TEXT);
        }
        if (content.isEmpty()) {
            throw failure(Reason.INVALID_TEXT);
        }
        try {
            return new ExtractedText(document.name(), content);
        } catch (IllegalArgumentException exception) {
            throw failure(Reason.TOO_LARGE);
        }
    }

    private static TextExtractionException failure(Reason reason) {
        return new TextExtractionException(reason);
    }
}
