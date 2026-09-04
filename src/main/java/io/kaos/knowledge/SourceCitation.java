package io.kaos.knowledge;

import java.util.Objects;

/** One stable, content-free reference to evidence included in a grounded prompt. */
public record SourceCitation(
        int number,
        long documentIdentifier,
        String sourceName,
        int chunkIndex) {
    public SourceCitation {
        if (number <= 0 || number > RelevantContextRetriever.MAX_RESULTS) {
            throw new IllegalArgumentException("citation number must be bounded and positive");
        }
        if (documentIdentifier <= 0) {
            throw new IllegalArgumentException("documentIdentifier must be positive");
        }
        Objects.requireNonNull(sourceName, "sourceName");
        if (sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must not be negative");
        }
    }

    public String label() {
        return "[" + number + "]";
    }

    /** Returns a single-line representation suitable for local diagnostics. */
    public String printableSourceName() {
        return sourceName.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
