package io.kaos.tool.httpget;

import java.util.Objects;

/** Internal classified failure without retaining URL or response content. */
public final class HttpGetException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public HttpGetException(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_CONFIGURATION,
        INVALID_REQUEST,
        DISALLOWED_HOST,
        NON_PUBLIC_DESTINATION,
        UNAVAILABLE,
        TIMEOUT,
        INTERRUPTED,
        REQUEST_FAILED,
        REDIRECTED,
        UNSUPPORTED_MEDIA_TYPE,
        TOO_LARGE,
        INVALID_UTF8,
        INVALID_CONTENT,
        APPROVAL_REUSED
    }
}
