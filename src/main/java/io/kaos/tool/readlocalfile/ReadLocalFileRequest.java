package io.kaos.tool.readlocalfile;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** One structurally valid relative path requested by {@code read_local_file}. */
public record ReadLocalFileRequest(String path) {
    public static final int MAX_PATH_CODE_POINTS = 512;

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".txt", ".md", ".log", ".java", ".kt", ".kts", ".gradle",
            ".json", ".xml", ".yaml", ".yml", ".properties", ".csv");

    public ReadLocalFileRequest {
        if (path == null) {
            throw new IllegalArgumentException("tool path must not be null");
        }
        if (path.isBlank()) {
            throw new IllegalArgumentException("tool path must not be blank");
        }
        if (path.codePointCount(0, path.length()) > MAX_PATH_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "tool path must contain at most " + MAX_PATH_CODE_POINTS + " characters");
        }
        if (path.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("tool path contains an unsupported control character");
        }

        validatePortableRelativeShape(path);
        validateExtension(fileName(path));
    }

    private static void validatePortableRelativeShape(String path) {
        if (path.startsWith("/") || path.startsWith("\\") || path.indexOf(':') >= 0) {
            throw relativePathRequired();
        }

        String[] segments = path.split("[\\\\/]", -1);
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw relativePathRequired();
            }
        }

        try {
            if (Path.of(path).isAbsolute()) {
                throw relativePathRequired();
            }
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("tool path is invalid", exception);
        }
    }

    private static String fileName(String path) {
        int forwardSlash = path.lastIndexOf('/');
        int backwardSlash = path.lastIndexOf('\\');
        return path.substring(Math.max(forwardSlash, backwardSlash) + 1);
    }

    private static void validateExtension(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        boolean supported = SUPPORTED_EXTENSIONS.stream().anyMatch(normalized::endsWith);
        if (!supported) {
            throw new IllegalArgumentException("tool path must use a supported text extension");
        }
    }

    private static IllegalArgumentException relativePathRequired() {
        return new IllegalArgumentException("tool path must be one normalized relative file path");
    }

    @Override
    public String toString() {
        return "ReadLocalFileRequest[path=REDACTED]";
    }
}
