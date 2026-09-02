package io.kaos.knowledge;

import java.util.Objects;

/** Reports a stable, content-free reason for rejecting an admitted document's text. */
public final class TextExtractionException extends Exception {
    /** Extraction failures safe to map at the application boundary. */
    public enum Reason {
        UNSUPPORTED_MEDIA_TYPE,
        INVALID_TEXT,
        TOO_LARGE
    }

    private final Reason reason;

    TextExtractionException(Reason reason) {
        super("Admitted document text extraction failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
