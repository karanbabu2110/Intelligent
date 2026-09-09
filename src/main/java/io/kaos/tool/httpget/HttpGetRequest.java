package io.kaos.tool.httpget;

/** One untrusted URL string requested by {@code http_get}. */
public record HttpGetRequest(String url) {
    public static final int MAX_URL_CODE_POINTS = 2_048;

    public HttpGetRequest {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HTTP GET URL must not be blank.");
        }
        if (!url.equals(url.strip())
                || url.codePointCount(0, url.length()) > MAX_URL_CODE_POINTS
                || url.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("HTTP GET URL is invalid.");
        }
    }

    @Override
    public String toString() {
        return "HttpGetRequest[url=REDACTED]";
    }
}
