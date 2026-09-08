package io.kaos.tool.readlocalfile;

import java.util.Objects;

/** Maps only known local-file tool failures to fixed caller-facing diagnostics. */
public final class ReadLocalFileFailureMapper {
    private ReadLocalFileFailureMapper() {
    }

    public static ReadLocalFileFailure invalidRequest() {
        return ReadLocalFileFailure.INVALID_REQUEST;
    }

    public static ReadLocalFileFailure from(
            ReadLocalFilePermissionException exception) {
        Objects.requireNonNull(exception, "exception");
        return switch (exception.reason()) {
            case INVALID_CONFIGURATION -> ReadLocalFileFailure.INVALID_CONFIGURATION;
            case UNAVAILABLE -> ReadLocalFileFailure.UNAVAILABLE;
            case OUTSIDE_ROOT -> ReadLocalFileFailure.OUTSIDE_ROOT;
            case INVALID_TARGET -> ReadLocalFileFailure.INVALID_TARGET;
            case UNREADABLE -> ReadLocalFileFailure.UNREADABLE;
            case TOO_LARGE -> ReadLocalFileFailure.TOO_LARGE;
            case CHANGED -> ReadLocalFileFailure.CHANGED;
        };
    }

    public static ReadLocalFileFailure from(
            ReadLocalFileExecutionException exception) {
        Objects.requireNonNull(exception, "exception");
        return switch (exception.reason()) {
            case UNAVAILABLE -> ReadLocalFileFailure.UNAVAILABLE;
            case CHANGED -> ReadLocalFileFailure.CHANGED;
            case INVALID_UTF8 -> ReadLocalFileFailure.INVALID_UTF8;
            case INVALID_CONTENT -> ReadLocalFileFailure.INVALID_CONTENT;
            case CANCELLED -> ReadLocalFileFailure.CANCELLED;
        };
    }

    public static ReadLocalFileFailure invalidState() {
        return ReadLocalFileFailure.INVALID_STATE;
    }
}
