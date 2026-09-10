package io.kaos.agent;

import java.util.Objects;

/** Content-free rejection of an invalid execution-state transition. */
public final class AgentExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason { INVALID_TRANSITION, WRONG_STEP, TERMINAL_EXECUTION }

    private final Reason reason;

    public AgentExecutionException(Reason reason) {
        super("Agent execution transition rejected: " + reason);
        this.reason = Objects.requireNonNull(reason);
    }

    public Reason reason() { return reason; }
}
