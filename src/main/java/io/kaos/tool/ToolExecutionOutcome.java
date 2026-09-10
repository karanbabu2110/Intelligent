package io.kaos.tool;



/** Shared lifecycle vocabulary; terminal values may be copied into content-free history. */
public enum ToolExecutionOutcome {
    APPROVAL_REQUIRED, APPROVED, EXECUTING, SUCCEEDED, DENIED, INVALID, CANCELLED, FAILED;

    public boolean terminal() {
        return switch (this) {
            case SUCCEEDED, DENIED, INVALID, CANCELLED, FAILED -> true;
            case APPROVAL_REQUIRED, APPROVED, EXECUTING -> false;
        };
    }
}
