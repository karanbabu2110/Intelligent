package io.kaos.tool.readlocalfile;

import io.kaos.tool.readlocalfile.ReadLocalFileAuditRecord.Decision;
import io.kaos.tool.readlocalfile.ReadLocalFileAuditRecord.Outcome;
import java.util.Objects;
import java.util.UUID;

/** Assigns one opaque target identity and emits at most one final audit record. */
public final class ReadLocalFileAuditContext {
    private final UUID targetIdentity;
    private boolean recorded;

    private ReadLocalFileAuditContext(ReadLocalFileTarget target, UUID targetIdentity) {
        Objects.requireNonNull(target, "target");
        this.targetIdentity = Objects.requireNonNull(targetIdentity, "targetIdentity");
    }

    /** Starts content-free correlation for one exact validated target. */
    public static ReadLocalFileAuditContext start(ReadLocalFileTarget target) {
        return new ReadLocalFileAuditContext(target, UUID.randomUUID());
    }

    static ReadLocalFileAuditContext start(
            ReadLocalFileTarget target, UUID targetIdentity) {
        return new ReadLocalFileAuditContext(target, targetIdentity);
    }

    /** Records a denial or other pre-execution no-authority outcome. */
    public synchronized ReadLocalFileAuditRecord recordNotExecuted(
            ReadLocalFileApprovalOutcome approval) {
        Objects.requireNonNull(approval, "approval");
        Decision decision = switch (approval.status()) {
            case DENIED -> Decision.DENIED;
            case CANCELLED -> Decision.CANCELLED;
            case INVALID_RESPONSE -> Decision.INVALID_RESPONSE;
            case END_OF_INPUT -> Decision.END_OF_INPUT;
            case APPROVED -> throw new IllegalArgumentException(
                    "An approved decision requires an execution outcome.");
        };
        return finish(decision, Outcome.NOT_EXECUTED);
    }

    /** Records one successful execution after explicit approval. */
    public synchronized ReadLocalFileAuditRecord recordSucceeded() {
        return finish(Decision.APPROVED, Outcome.SUCCEEDED);
    }

    /** Records one failed execution after explicit approval without retaining failure detail. */
    public synchronized ReadLocalFileAuditRecord recordFailed() {
        return finish(Decision.APPROVED, Outcome.FAILED);
    }

    /** Records one cancelled execution after explicit approval. */
    public synchronized ReadLocalFileAuditRecord recordExecutionCancelled() {
        return finish(Decision.APPROVED, Outcome.CANCELLED);
    }

    private ReadLocalFileAuditRecord finish(Decision decision, Outcome outcome) {
        if (recorded) {
            throw new IllegalStateException("A final tool audit record already exists.");
        }
        recorded = true;
        return new ReadLocalFileAuditRecord(targetIdentity, decision, outcome);
    }

    @Override
    public synchronized String toString() {
        return "ReadLocalFileAuditContext[targetIdentity=" + targetIdentity
                + ", recorded=" + recorded + "]";
    }
}
