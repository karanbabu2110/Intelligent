package io.kaos.tool.browserrender;

import java.net.URI;
import java.util.Arrays;
import java.util.Objects;

/** One bounded response fetched for an isolated research render. */
public record BrowserResource(URI url, int status, String contentType, String location, byte[] body) {
    public BrowserResource {
        Objects.requireNonNull(url, "url");
        contentType = contentType == null ? "" : contentType;
        location = location == null ? "" : location;
        body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
    }

    @Override public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }
}
