package io.kaos.tool.readlocalfile;

/** One fixed privacy-safe diagnostic for a known local-file tool failure. */
public enum ReadLocalFileFailure {
    INVALID_REQUEST(
            "KAOS-TOOL-READ-001",
            "The local file tool request is invalid. Request one supported relative file "
                    + "path and retry."),
    INVALID_CONFIGURATION(
            "KAOS-TOOL-READ-002",
            "The local file read root is not configured safely. Configure one absolute "
                    + "non-filesystem-root directory and retry."),
    UNAVAILABLE(
            "KAOS-TOOL-READ-003",
            "The local file is unavailable. Verify the configured root and file "
                    + "exist and are accessible, then make a new request."),
    OUTSIDE_ROOT(
            "KAOS-TOOL-READ-004",
            "The requested file is outside the allowed root or traverses a link. Choose one "
                    + "direct file below the configured root."),
    INVALID_TARGET(
            "KAOS-TOOL-READ-005",
            "The requested target is not a non-empty regular file. Choose one supported "
                    + "text-based file and retry."),
    UNREADABLE(
            "KAOS-TOOL-READ-006",
            "The requested file is not readable by this process. Correct its local read "
                    + "permission, then make a new request."),
    TOO_LARGE(
            "KAOS-TOOL-READ-007",
            "The requested file exceeds the 2,048-byte tool limit. Choose a smaller file "
                    + "and retry."),
    CHANGED(
            "KAOS-TOOL-READ-008",
            "The approved file changed before the read completed. Make a new request and "
                    + "approve the newly validated target."),
    INVALID_UTF8(
            "KAOS-TOOL-READ-009",
            "The approved file is not strict UTF-8 text. Convert it to UTF-8 or choose a "
                    + "different supported file."),
    INVALID_CONTENT(
            "KAOS-TOOL-READ-010",
            "The approved file is blank or contains unsupported control data. Choose a "
                    + "non-empty text-based file."),
    CANCELLED(
            "KAOS-TOOL-READ-011",
            "The approved read was cancelled. Make a new request only when ready to approve "
                    + "another attempt."),
    INVALID_STATE(
            "KAOS-TOOL-READ-012",
            "The local file tool request is no longer active. Start a new request instead "
                    + "of reusing an approval or result.");

    private final String code;
    private final String message;

    ReadLocalFileFailure(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    /** Returns the stable application-compatible diagnostic without private values. */
    public String diagnostic() {
        return "ERROR [" + code + "] " + message;
    }
}
