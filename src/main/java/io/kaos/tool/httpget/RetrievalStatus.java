package io.kaos.tool.httpget;

/** Stable high-level outcome categories for one publisher retrieval attempt. */
public enum RetrievalStatus {
    SUCCESS, TOO_LARGE, HTTP_ERROR, TIMEOUT, UNAVAILABLE, UNSUPPORTED_CONTENT
}
