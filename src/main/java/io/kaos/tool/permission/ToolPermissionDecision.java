package io.kaos.tool.permission;



/** Shared local-user decision vocabulary, independent of concrete grant types. */
public enum ToolPermissionDecision {
    APPROVED, DENIED, CANCELLED, INVALID_RESPONSE, END_OF_INPUT;

    public static ToolPermissionDecision parse(String response) {
        if (Thread.currentThread().isInterrupted()) return CANCELLED;
        if (response == null) return END_OF_INPUT;
        return switch (response.strip()) {
            case "approve" -> APPROVED;
            case "deny" -> DENIED;
            default -> INVALID_RESPONSE;
        };
    }
}
