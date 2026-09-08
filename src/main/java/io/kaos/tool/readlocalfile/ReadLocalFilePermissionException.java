package io.kaos.tool.readlocalfile;

/** Content-free failure while resolving or validating a local file tool target. */
public final class ReadLocalFilePermissionException extends RuntimeException {
    private final Reason reason;

    ReadLocalFilePermissionException(Reason reason) {
        super("Local file tool permission validation failed.");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Stable content-free categories for configuration, permission, and target failures. */
    public enum Reason {
        INVALID_CONFIGURATION,
        UNAVAILABLE,
        OUTSIDE_ROOT,
        INVALID_TARGET,
        UNREADABLE,
        TOO_LARGE,
        CHANGED
    }
}
