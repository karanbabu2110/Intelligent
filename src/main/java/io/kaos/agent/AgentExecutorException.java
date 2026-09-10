package io.kaos.agent;

import java.util.Objects;

/** Content-free rejection of an invalid agent tool-step attempt. */
public final class AgentExecutorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        NOT_TOOL_STEP,
        DISALLOWED_TOOL,
        NOT_PREPARED,
        PLAN_COMPLETE,
        EXECUTION_STOPPED,
        INVALID_TOOL_RESULT
    }

    private final Reason reason;

    public AgentExecutorException(Reason reason) {
        super("Agent tool step failed: " + reason);
        this.reason = Objects.requireNonNull(reason);
    }

    public Reason reason() {
        return reason;
    }
}
