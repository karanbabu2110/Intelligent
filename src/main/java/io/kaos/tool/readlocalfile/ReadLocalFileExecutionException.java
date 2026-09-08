package io.kaos.tool.readlocalfile;

import java.util.Objects;

/** Content-free failure while consuming an approved local-file read attempt. */
public final class ReadLocalFileExecutionException extends RuntimeException {
    private final Reason reason;

    ReadLocalFileExecutionException(Reason reason) {
        super("Approved local file tool execution failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    /** Stable categories owned by bounded content reading and decoding. */
    public enum Reason {
        UNAVAILABLE,
        CHANGED,
        INVALID_UTF8,
        INVALID_CONTENT,
        CANCELLED
    }
}
