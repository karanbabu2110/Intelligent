package io.kaos.tool.httpget;

import io.kaos.tool.permission.ToolPermissionDecision;
import java.util.Objects;
import java.util.UUID;

/** Produces one URL-free final audit value for one HTTP GET tool request. */
public final class HttpGetAudit {
    private final UUID targetIdentity;

    public HttpGetAudit() { this(UUID.randomUUID()); }

    public HttpGetAudit(UUID targetIdentity) {
        this.targetIdentity = Objects.requireNonNull(targetIdentity, "targetIdentity");
    }
    private boolean recorded;

    public Record notExecuted(HttpGetApproval.Outcome approval) {
        Objects.requireNonNull(approval, "approval");
        return notExecuted(ToolPermissionDecision.valueOf(approval.status().name()));
    }

    public Record notExecuted(ToolPermissionDecision approval) {
        Decision decision = switch (approval) {
            case DENIED -> Decision.DENIED;
            case CANCELLED -> Decision.CANCELLED;
            case INVALID_RESPONSE -> Decision.INVALID_RESPONSE;
            case END_OF_INPUT -> Decision.END_OF_INPUT;
            case APPROVED -> throw new IllegalArgumentException("Approved request was executed.");
        };
        return finish(decision, Outcome.NOT_EXECUTED);
    }

    public Record succeeded() {
        return finish(Decision.APPROVED, Outcome.SUCCEEDED);
    }

    public Record failed() {
        return finish(Decision.APPROVED, Outcome.FAILED);
    }

    public Record cancelled() {
        return finish(Decision.APPROVED, Outcome.CANCELLED);
    }

    private synchronized Record finish(Decision decision, Outcome outcome) {
        if (recorded) throw new IllegalStateException("HTTP GET audit is already final.");
        recorded = true;
        return new Record(targetIdentity, decision, outcome);
    }

    public record Record(UUID targetIdentity, Decision decision, Outcome outcome) {
        public Record {
            Objects.requireNonNull(targetIdentity, "targetIdentity");
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(outcome, "outcome");
            if ((outcome != Outcome.NOT_EXECUTED) != (decision == Decision.APPROVED)) {
                throw new IllegalArgumentException(
                        "Only approved HTTP GET requests may have execution outcomes.");
            }
        }

        public String operation() {
            return HttpGetToolContract.NAME;
        }
    }

    public enum Decision { APPROVED, DENIED, CANCELLED, INVALID_RESPONSE, END_OF_INPUT }
    public enum Outcome { SUCCEEDED, FAILED, CANCELLED, NOT_EXECUTED }
}
