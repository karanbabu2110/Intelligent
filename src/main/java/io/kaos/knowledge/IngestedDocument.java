package io.kaos.knowledge;

import java.util.Objects;

/** One immutable document admitted for later knowledge processing. */
public record IngestedDocument(String name, String mediaType, byte[] content) {
    public IngestedDocument {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(content, "content");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (mediaType.isBlank()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    /** Returns the exact number of admitted source bytes. */
    public int byteCount() {
        return content.length;
    }
}
