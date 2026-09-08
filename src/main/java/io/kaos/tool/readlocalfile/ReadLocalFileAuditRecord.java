package io.kaos.tool.readlocalfile;

import java.util.Objects;
import java.util.UUID;

/** One immutable content-free final audit value for one local-file tool invocation. */
public final class ReadLocalFileAuditRecord {
    public static final String OPERATION = ReadLocalFileToolContract.NAME;

    private final UUID targetIdentity;
    private final Decision decision;
    private final Outcome outcome;

    ReadLocalFileAuditRecord(UUID targetIdentity, Decision decision, Outcome outcome) {
        this.targetIdentity = Objects.requireNonNull(targetIdentity, "targetIdentity");
        this.decision = Objects.requireNonNull(decision, "decision");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        boolean executed = outcome != Outcome.NOT_EXECUTED;
        if (executed != (decision == Decision.APPROVED)) {
            throw new IllegalArgumentException(
                    "Only an approved decision may have an execution outcome.");
        }
    }

    public String operation() {
        return OPERATION;
    }

    /** Returns a random per-invocation correlation value that is not derived from the path. */
    public UUID targetIdentity() {
        return targetIdentity;
    }

    public Decision decision() {
        return decision;
    }

    public Outcome outcome() {
        return outcome;
    }

    public enum Decision {
        APPROVED,
        DENIED,
        CANCELLED,
        INVALID_RESPONSE,
        END_OF_INPUT
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        CANCELLED,
        NOT_EXECUTED
    }

    @Override
    public String toString() {
        return "ReadLocalFileAuditRecord[operation=" + OPERATION
                + ", targetIdentity=" + targetIdentity
                + ", decision=" + decision
                + ", outcome=" + outcome + "]";
    }
}
