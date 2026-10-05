package io.kaos.tool.browserrender;

import java.util.Objects;

/** Content-free classified failure for one explicitly approved research render. */
public final class BrowserRenderException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    public BrowserRenderException(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_REQUEST,
        NON_PUBLIC_DESTINATION,
        REQUEST_BLOCKED,
        REDIRECT_BLOCKED,
        REQUEST_LIMIT,
        REDIRECT_LIMIT,
        TOO_LARGE,
        TIMEOUT,
        HTTP_ERROR,
        ACCESS_DENIED,
        RATE_LIMITED,
        CAPTCHA,
        UNSUPPORTED_LAYOUT,
        UNSUPPORTED_CONTENT,
        EMPTY_CONTENT,
        UNAVAILABLE,
        INTERRUPTED
    }
}
