package io.kaos.agent;

/** Content-free reason for an incomplete bounded agent workflow. */
public enum AgentFailureReason {
    INVALID_GOAL,
    INVALID_PLAN,
    TOOL_NOT_ALLOWED,
    TOOL_CONFIGURATION_UNAVAILABLE,
    TOOL_VALIDATION_FAILED,
    PERMISSION_DENIED,
    INVALID_APPROVAL,
    END_OF_INPUT,
    CANCELLED,
    INTERRUPTED,
    TOOL_EXECUTION_FAILED,
    TOOL_HISTORY_FAILED,
    CURRENT_EVIDENCE_UNAVAILABLE,
    MODEL_PROVIDER_FAILED,
    INVALID_TOOL_RESULT;

    public boolean cancellation() {
        return this == END_OF_INPUT || this == CANCELLED || this == INTERRUPTED;
    }
}
