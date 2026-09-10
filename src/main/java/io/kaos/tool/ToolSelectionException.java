package io.kaos.tool;

import java.util.Objects;

/** Safe selection diagnostic; never includes generated arguments or a model-supplied name. */
public final class ToolSelectionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public enum Reason { UNKNOWN_TOOL, DISALLOWED_TOOL, MALFORMED_ARGUMENTS, INVALID_RESPONSE }
    private final Reason reason;
    public ToolSelectionException(Reason reason) {
        super("Tool selection failed: " + reason);
        this.reason = Objects.requireNonNull(reason);
    }
    public Reason reason() { return reason; }
}
