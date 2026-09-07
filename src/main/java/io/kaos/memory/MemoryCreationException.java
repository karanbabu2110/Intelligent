package io.kaos.memory;

/** Reports a bounded, content-free reason that explicit memory creation failed. */
public final class MemoryCreationException extends RuntimeException {
    public enum Reason {
        INVALID_KEY,
        INVALID_VALUE,
        ALREADY_EXISTS
    }

    private final Reason reason;

    private MemoryCreationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    static MemoryCreationException invalidKey() {
        return new MemoryCreationException(Reason.INVALID_KEY);
    }

    static MemoryCreationException invalidValue() {
        return new MemoryCreationException(Reason.INVALID_VALUE);
    }

    static MemoryCreationException alreadyExists() {
        return new MemoryCreationException(Reason.ALREADY_EXISTS);
    }
}
