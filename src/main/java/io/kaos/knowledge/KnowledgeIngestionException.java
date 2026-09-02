package io.kaos.knowledge;

import java.util.Objects;

/** Reports a stable, content-free reason for rejecting a local document. */
public final class KnowledgeIngestionException extends Exception {
    /** Failure classes safe to map at the application boundary. */
    public enum Reason {
        INVALID_DOCUMENT,
        UNAVAILABLE,
        TOO_LARGE
    }

    private final Reason reason;

    KnowledgeIngestionException(Reason reason) {
        super("Local text document ingestion failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
