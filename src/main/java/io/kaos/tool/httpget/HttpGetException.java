package io.kaos.tool.httpget;

import java.util.Objects;
import java.util.Map;

/** Internal classified failure without retaining URL or response content. */
public final class HttpGetException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final int httpStatus;
    private final String contentType;
    private final String finalUrl;
    private final Map<String, String> responseHeaders;
    private final String failureDetail;

    public HttpGetException(Reason reason) {
        this(reason, 0, "", "", Map.of(), "");
    }

    public HttpGetException(Reason reason, int httpStatus, String contentType, String finalUrl) {
        this(reason, httpStatus, contentType, finalUrl, Map.of(), "");
    }

    public HttpGetException(Reason reason, int httpStatus, String contentType, String finalUrl,
            Map<String, String> responseHeaders) {
        this(reason, httpStatus, contentType, finalUrl, responseHeaders, "");
    }

    public HttpGetException(Reason reason, int httpStatus, String contentType, String finalUrl,
            Map<String, String> responseHeaders, String failureDetail) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
        this.httpStatus = httpStatus;
        this.contentType = contentType == null ? "" : contentType;
        this.finalUrl = finalUrl == null ? "" : finalUrl;
        this.responseHeaders = Map.copyOf(responseHeaders == null ? Map.of() : responseHeaders);
        this.failureDetail = failureDetail == null ? "" : failureDetail;
    }

    public Reason reason() {
        return reason;
    }

    public int httpStatus() { return httpStatus; }
    public String contentType() { return contentType; }
    public String finalUrl() { return finalUrl; }
    public Map<String, String> responseHeaders() { return responseHeaders; }
    public String failureDetail() { return failureDetail; }
    public RetrievalStatus status() {
        return switch (reason) {
            case TOO_LARGE -> RetrievalStatus.TOO_LARGE;
            case HTTP_UNAUTHORIZED, HTTP_FORBIDDEN, HTTP_NOT_FOUND, HTTP_RATE_LIMITED,
                    REQUEST_FAILED, REDIRECTED -> RetrievalStatus.HTTP_ERROR;
            case TIMEOUT -> RetrievalStatus.TIMEOUT;
            case UNSUPPORTED_MEDIA_TYPE, INVALID_CONTENT, INVALID_UTF8 -> RetrievalStatus.UNSUPPORTED_CONTENT;
            default -> RetrievalStatus.UNAVAILABLE;
        };
    }

    public enum Reason {
        INVALID_CONFIGURATION,
        INVALID_REQUEST,
        DISALLOWED_HOST,
        NON_PUBLIC_DESTINATION,
        UNAVAILABLE,
        TIMEOUT,
        INTERRUPTED,
        HTTP_UNAUTHORIZED,
        HTTP_FORBIDDEN,
        HTTP_NOT_FOUND,
        HTTP_RATE_LIMITED,
        REQUEST_FAILED,
        REDIRECTED,
        UNSUPPORTED_MEDIA_TYPE,
        TOO_LARGE,
        INVALID_UTF8,
        INVALID_CONTENT,
        APPROVAL_REUSED
    }
}
