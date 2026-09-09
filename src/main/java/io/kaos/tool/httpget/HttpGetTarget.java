package io.kaos.tool.httpget;

import java.net.URI;
import java.util.Objects;

/** One syntactically valid request bound to an explicitly configured host. */
public record HttpGetTarget(HttpGetRequest request, URI uri) {
    public HttpGetTarget {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(uri, "uri");
    }

    @Override
    public String toString() {
        return "HttpGetTarget[uri=REDACTED]";
    }
}
