package io.kaos.memory;

import java.util.Objects;

/** Reports a safe failure of an explicit edit or deletion transition. */
public final class MemoryMutationException extends RuntimeException {
    public enum Reason {
        INVALID_KEY,
        INVALID_VALUE,
        ABSENT
    }

    private final Reason reason;

    private MemoryMutationException(Reason reason) {
        super("Memory mutation failed.");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    static MemoryMutationException invalidKey() {
        return new MemoryMutationException(Reason.INVALID_KEY);
    }

    static MemoryMutationException invalidValue() {
        return new MemoryMutationException(Reason.INVALID_VALUE);
    }

    static MemoryMutationException absent() {
        return new MemoryMutationException(Reason.ABSENT);
    }
}
