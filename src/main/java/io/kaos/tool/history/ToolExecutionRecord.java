package io.kaos.tool.history;

import io.kaos.tool.ToolExecutionOutcome;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Content-free terminal history for one tool permission lifecycle. */
public record ToolExecutionRecord(String toolName, UUID operationId,
        Optional<ToolPermissionDecision> decision, ToolExecutionOutcome outcome,
        Instant startedAt, Instant completedAt) {

    public ToolExecutionRecord {
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(operationId, "operationId");
        decision = Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (!toolName.matches("[a-z][a-z0-9_]*")
                || completedAt.isBefore(startedAt) || !outcome.terminal()) {
            throw new IllegalArgumentException("Invalid terminal tool execution record.");
        }
        ToolPermissionDecision value = decision.orElse(null);
        boolean consistent = switch (outcome) {
            case SUCCEEDED -> value == ToolPermissionDecision.APPROVED;
            case DENIED -> value == ToolPermissionDecision.DENIED;
            case INVALID -> value == ToolPermissionDecision.INVALID_RESPONSE;
            case CANCELLED -> value == ToolPermissionDecision.APPROVED
                    || value == ToolPermissionDecision.CANCELLED
                    || value == ToolPermissionDecision.END_OF_INPUT;
            case FAILED -> value == null || value == ToolPermissionDecision.APPROVED;
            case APPROVAL_REQUIRED, APPROVED, EXECUTING -> false;
        };
        if (!consistent) {
            throw new IllegalArgumentException("Tool decision and terminal outcome disagree.");
        }
    }

    public static ToolExecutionRecord from(ToolPermissionPolicy.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new ToolExecutionRecord(snapshot.toolName(), snapshot.operationId(),
                snapshot.decision(), snapshot.outcome(), snapshot.startedAt(), snapshot.updatedAt());
    }

    @Override
    public String toString() {
        return "ToolExecutionRecord[toolName=" + toolName + ", operationId=" + operationId
                + ", decision=" + decision.map(Enum::name).orElse("NOT_RECORDED")
                + ", outcome=" + outcome + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt + "]";
    }
}
