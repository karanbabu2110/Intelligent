package io.kaos.agent;

import java.util.Objects;

/** Content-free rejection of an invalid model plan proposal. */
public final class AgentPlanningException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        MALFORMED_RESPONSE,
        TOO_MANY_STEPS,
        TOO_MANY_TOOL_STEPS,
        INVALID_SEQUENCE,
        INVALID_STRUCTURE,
        FRESHNESS_REQUIRED,
        UNKNOWN_TOOL,
        DISALLOWED_TOOL,
        MALFORMED_ARGUMENTS
    }

    private final Reason reason;

    public AgentPlanningException(Reason reason) {
        super("Agent plan rejected: " + reason);
        this.reason = Objects.requireNonNull(reason);
    }

    public Reason reason() {
        return reason;
    }

    static AgentPlanningException invalidStructure() {
        return new AgentPlanningException(Reason.INVALID_STRUCTURE);
    }
}
