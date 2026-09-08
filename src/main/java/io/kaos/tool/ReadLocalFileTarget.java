package io.kaos.tool;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/** One metadata-validated target below the configured local read root. */
public final class ReadLocalFileTarget {
    private final ReadLocalFileRequest request;
    private final Path resolvedPath;
    private final long byteCount;
    private final FileTime lastModifiedTime;
    private final Object fileKey;

    ReadLocalFileTarget(ReadLocalFileRequest request, Path resolvedPath, long byteCount,
            FileTime lastModifiedTime, Object fileKey) {
        this.request = Objects.requireNonNull(request, "request");
        this.resolvedPath = Objects.requireNonNull(resolvedPath, "resolvedPath");
        if (!resolvedPath.isAbsolute() || !resolvedPath.equals(resolvedPath.normalize())) {
            throw new IllegalArgumentException("resolved tool target must be absolute and normalized");
        }
        if (byteCount <= 0 || byteCount > ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES) {
            throw new IllegalArgumentException("resolved tool target byte count is invalid");
        }
        this.byteCount = byteCount;
        this.lastModifiedTime = Objects.requireNonNull(lastModifiedTime, "lastModifiedTime");
        this.fileKey = fileKey;
    }

    public ReadLocalFileRequest request() {
        return request;
    }

    /** Returns the exact validated target for an explicit user approval display. */
    public Path resolvedPath() {
        return resolvedPath;
    }

    public long byteCount() {
        return byteCount;
    }

    FileTime lastModifiedTime() {
        return lastModifiedTime;
    }

    Object fileKey() {
        return fileKey;
    }

    @Override
    public String toString() {
        return "ReadLocalFileTarget[path=REDACTED, byteCount=" + byteCount + "]";
    }
}
