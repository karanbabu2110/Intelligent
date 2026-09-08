package io.kaos.tool.readlocalfile;

import io.kaos.tool.readlocalfile.ReadLocalFilePermissionException.Reason;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Resolves one least-privilege read root and validates one metadata-only target. */
public final class ReadLocalFilePermissionValidator {
    public static final String ROOT_SYSTEM_PROPERTY = "kaos.tool.read-root";
    public static final String ROOT_ENVIRONMENT_VARIABLE = "KAOS_TOOL_READ_ROOT";

    private final Path configuredRoot;

    public ReadLocalFilePermissionValidator(Path configuredRoot) {
        Objects.requireNonNull(configuredRoot, "configuredRoot");
        if (!configuredRoot.isAbsolute()) {
            throw failure(Reason.INVALID_CONFIGURATION);
        }
        this.configuredRoot = configuredRoot.normalize();
        if (this.configuredRoot.getParent() == null) {
            throw failure(Reason.INVALID_CONFIGURATION);
        }
    }

    /** Loads one mandatory root, preferring the system property over the environment. */
    public static ReadLocalFilePermissionValidator load() {
        try {
            return configured(
                    System.getProperty(ROOT_SYSTEM_PROPERTY),
                    System.getenv(ROOT_ENVIRONMENT_VARIABLE));
        } catch (SecurityException exception) {
            throw failure(Reason.INVALID_CONFIGURATION);
        }
    }

    static ReadLocalFilePermissionValidator configured(String property, String environment) {
        String selected = property != null ? property : environment;
        if (selected == null || selected.isBlank()) {
            throw failure(Reason.INVALID_CONFIGURATION);
        }
        try {
            return new ReadLocalFilePermissionValidator(Path.of(selected));
        } catch (InvalidPathException exception) {
            throw failure(Reason.INVALID_CONFIGURATION);
        }
    }

    /** Validates metadata and returns an exact target without opening or decoding its content. */
    public ReadLocalFileTarget validate(ReadLocalFileRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            BasicFileAttributes rootAttributes = attributes(configuredRoot);
            if (rootAttributes.isSymbolicLink() || !rootAttributes.isDirectory()) {
                throw failure(Reason.INVALID_CONFIGURATION);
            }
            Path realRoot = configuredRoot.toRealPath();
            Path candidate = realRoot.resolve(request.path()).normalize();
            if (!candidate.startsWith(realRoot)) {
                throw failure(Reason.OUTSIDE_ROOT);
            }

            rejectLinkedSegments(realRoot, request);
            BasicFileAttributes targetAttributes = attributes(candidate);
            if (targetAttributes.isSymbolicLink() || !targetAttributes.isRegularFile()
                    || targetAttributes.size() == 0) {
                throw failure(Reason.INVALID_TARGET);
            }
            if (targetAttributes.size() > ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES) {
                throw failure(Reason.TOO_LARGE);
            }
            if (!Files.isReadable(candidate)) {
                throw failure(Reason.UNREADABLE);
            }

            Path realTarget = candidate.toRealPath();
            if (!realTarget.startsWith(realRoot)) {
                throw failure(Reason.OUTSIDE_ROOT);
            }
            BasicFileAttributes stableAttributes = attributes(realTarget);
            if (!sameIdentity(targetAttributes, stableAttributes)) {
                throw failure(Reason.CHANGED);
            }
            return new ReadLocalFileTarget(
                    request,
                    realTarget,
                    stableAttributes.size(),
                    stableAttributes.lastModifiedTime(),
                    stableAttributes.fileKey());
        } catch (ReadLocalFilePermissionException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(Reason.UNAVAILABLE);
        }
    }

    /** Revalidates that an approved target still identifies the same allowed file metadata. */
    public ReadLocalFileTarget revalidate(ReadLocalFileTarget target) {
        Objects.requireNonNull(target, "target");
        ReadLocalFileTarget current = validate(target.request());
        if (!target.resolvedPath().equals(current.resolvedPath())
                || target.byteCount() != current.byteCount()
                || !target.lastModifiedTime().equals(current.lastModifiedTime())
                || !compatibleFileKeys(target.fileKey(), current.fileKey())) {
            throw failure(Reason.CHANGED);
        }
        return current;
    }

    private static void rejectLinkedSegments(Path realRoot, ReadLocalFileRequest request)
            throws IOException {
        Path current = realRoot;
        for (Path segment : Path.of(request.path())) {
            current = current.resolve(segment);
            BasicFileAttributes attributes = attributes(current);
            if (attributes.isSymbolicLink()) {
                throw failure(Reason.OUTSIDE_ROOT);
            }
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean sameIdentity(
            BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime())
                && compatibleFileKeys(first.fileKey(), second.fileKey());
    }

    private static boolean compatibleFileKeys(Object first, Object second) {
        return first == null || second == null || first.equals(second);
    }

    private static ReadLocalFilePermissionException failure(Reason reason) {
        return new ReadLocalFilePermissionException(reason);
    }

    @Override
    public String toString() {
        return "ReadLocalFilePermissionValidator[root=REDACTED]";
    }
}
