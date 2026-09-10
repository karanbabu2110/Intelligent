package io.kaos.tool;



/** Observable in-memory lifecycle; no persisted state machine or history store. */
public enum ToolExecutionOutcome {
    APPROVAL_REQUIRED, APPROVED, EXECUTING, SUCCEEDED, DENIED, INVALID, CANCELLED, FAILED
}
