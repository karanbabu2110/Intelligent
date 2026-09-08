package io.kaos.tool.readlocalfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.tool.readlocalfile.ReadLocalFilePermissionException.Reason;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class ReadLocalFilePermissionValidatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void selectsThePropertyBeforeTheEnvironmentAndRequiresExplicitConfiguration() {
        ReadLocalFilePermissionValidator property =
                ReadLocalFilePermissionValidator.configured(
                        temporaryDirectory.toString(), "ignored");
        ReadLocalFilePermissionValidator environment =
                ReadLocalFilePermissionValidator.configured(
                        null, temporaryDirectory.toString());

        assertEquals("ReadLocalFilePermissionValidator[root=REDACTED]", property.toString());
        assertEquals("ReadLocalFilePermissionValidator[root=REDACTED]", environment.toString());
        assertFailure(() -> ReadLocalFilePermissionValidator.configured(null, null),
                Reason.INVALID_CONFIGURATION);
        assertFailure(() -> ReadLocalFilePermissionValidator.configured(" ", "ignored"),
                Reason.INVALID_CONFIGURATION);
        assertFailure(() -> ReadLocalFilePermissionValidator.configured(
                "relative-root", null), Reason.INVALID_CONFIGURATION);
        assertFailure(() -> new ReadLocalFilePermissionValidator(
                temporaryDirectory.toAbsolutePath().getRoot()), Reason.INVALID_CONFIGURATION);
    }

    @Test
    void validatesOneNestedRegularFileWithoutReadingItsContent() throws Exception {
        Path sourceDirectory = Files.createDirectories(temporaryDirectory.resolve("src"));
        byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28};
        Path file = sourceDirectory.resolve("Main.java");
        Files.write(file, invalidUtf8);
        ReadLocalFileRequest request = new ReadLocalFileRequest("src/Main.java");

        ReadLocalFileTarget target = validator().validate(request);

        assertEquals(request, target.request());
        assertEquals(file.toRealPath(), target.resolvedPath());
        assertEquals(invalidUtf8.length, target.byteCount());
        assertFalse(target.toString().contains(file.toString()));
    }

    @Test
    void acceptsTheExactFileByteLimit() throws Exception {
        Path file = temporaryDirectory.resolve("limit.txt");
        Files.write(file, new byte[ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES]);

        assertEquals(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES,
                validator().validate(new ReadLocalFileRequest("limit.txt")).byteCount());
    }

    @Test
    void rejectsMissingAndNonDirectoryRoots() throws Exception {
        assertFailure(() -> new ReadLocalFilePermissionValidator(
                temporaryDirectory.resolve("missing")).validate(
                        new ReadLocalFileRequest("file.txt")), Reason.UNAVAILABLE);

        Path regularFile = temporaryDirectory.resolve("root.txt");
        Files.writeString(regularFile, "content", StandardCharsets.UTF_8);
        assertFailure(() -> new ReadLocalFilePermissionValidator(regularFile).validate(
                new ReadLocalFileRequest("file.txt")), Reason.INVALID_CONFIGURATION);

    }

    @Test
    void rejectsALinkedRoot() throws Exception {
        Path realRoot = Files.createDirectory(temporaryDirectory.resolve("real-root"));
        Path linkedRoot = temporaryDirectory.resolve("linked-root");
        createSymbolicLinkOrAbort(linkedRoot, realRoot);
        assertFailure(() -> new ReadLocalFilePermissionValidator(linkedRoot).validate(
                new ReadLocalFileRequest("file.txt")), Reason.INVALID_CONFIGURATION);
    }

    @Test
    void rejectsMissingDirectoryEmptyAndOversizedTargets() throws Exception {
        assertFailure(() -> validator().validate(new ReadLocalFileRequest("missing.txt")),
                Reason.UNAVAILABLE);

        Files.createDirectory(temporaryDirectory.resolve("directory.java"));
        assertFailure(() -> validator().validate(new ReadLocalFileRequest("directory.java")),
                Reason.INVALID_TARGET);

        Files.createFile(temporaryDirectory.resolve("empty.txt"));
        assertFailure(() -> validator().validate(new ReadLocalFileRequest("empty.txt")),
                Reason.INVALID_TARGET);

        Files.write(temporaryDirectory.resolve("large.txt"),
                new byte[ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES + 1]);
        assertFailure(() -> validator().validate(new ReadLocalFileRequest("large.txt")),
                Reason.TOO_LARGE);
    }

    @Test
    void rejectsALinkedTarget() throws Exception {
        Path target = temporaryDirectory.resolve("target.txt");
        Files.writeString(target, "private", StandardCharsets.UTF_8);
        Path link = temporaryDirectory.resolve("link.txt");
        createSymbolicLinkOrAbort(link, target.getFileName());
        assertFailure(() -> validator().validate(new ReadLocalFileRequest("link.txt")),
                Reason.OUTSIDE_ROOT);

    }

    @Test
    void rejectsALinkedAncestorEscape() throws Exception {
        Path outside = Files.createDirectory(temporaryDirectory.resolveSibling(
                temporaryDirectory.getFileName() + "-outside"));
        try {
            Files.writeString(outside.resolve("secret.java"), "private", StandardCharsets.UTF_8);
            Path linkedDirectory = temporaryDirectory.resolve("linked");
            createSymbolicLinkOrAbort(linkedDirectory, outside);
            assertFailure(() -> validator().validate(
                    new ReadLocalFileRequest("linked/secret.java")), Reason.OUTSIDE_ROOT);
        } finally {
            Files.deleteIfExists(outside.resolve("secret.java"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsAWindowsJunctionAncestorEscape() throws Exception {
        Path outside = Files.createDirectory(temporaryDirectory.resolveSibling(
                temporaryDirectory.getFileName() + "-junction-outside"));
        Path junction = temporaryDirectory.resolve("junction");
        try {
            Files.writeString(outside.resolve("secret.java"), "private", StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(
                    "cmd.exe", "/c", "mklink", "/J",
                    junction.toString(), outside.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Assumptions.assumeTrue(process.waitFor() == 0,
                    "Directory junctions are unavailable to this test process.");

            assertFailure(() -> validator().validate(
                    new ReadLocalFileRequest("junction/secret.java")), Reason.OUTSIDE_ROOT);
        } finally {
            Files.deleteIfExists(junction);
            Files.deleteIfExists(outside.resolve("secret.java"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void detectsAChangedTargetDuringRevalidation() throws Exception {
        Path file = temporaryDirectory.resolve("changing.txt");
        Files.writeString(file, "first", StandardCharsets.UTF_8);
        ReadLocalFilePermissionValidator validator = validator();
        ReadLocalFileTarget target = validator.validate(
                new ReadLocalFileRequest("changing.txt"));

        Files.writeString(file, "different length", StandardCharsets.UTF_8);

        assertFailure(() -> validator.revalidate(target), Reason.CHANGED);
    }

    @Test
    void revalidationReturnsTheSameTargetWhenMetadataIsUnchanged() throws Exception {
        Path file = temporaryDirectory.resolve("stable.txt");
        Files.writeString(file, "stable", StandardCharsets.UTF_8);
        ReadLocalFilePermissionValidator validator = validator();
        ReadLocalFileTarget first = validator.validate(new ReadLocalFileRequest("stable.txt"));

        ReadLocalFileTarget second = validator.revalidate(first);

        assertEquals(first.resolvedPath(), second.resolvedPath());
        assertEquals(first.byteCount(), second.byteCount());
    }

    @Test
    void failuresAndValuesDoNotExposeConfiguredOrRequestedPaths() {
        String privatePath = "private/customer.java";
        ReadLocalFilePermissionException exception = assertThrows(
                ReadLocalFilePermissionException.class,
                () -> validator().validate(new ReadLocalFileRequest(privatePath)));

        assertFalse(exception.getMessage().contains(privatePath));
        assertEquals("Local file tool permission validation failed.", exception.getMessage());
    }

    private ReadLocalFilePermissionValidator validator() {
        return new ReadLocalFilePermissionValidator(temporaryDirectory);
    }

    private static void assertFailure(Runnable operation, Reason reason) {
        ReadLocalFilePermissionException exception = assertThrows(
                ReadLocalFilePermissionException.class, operation::run);
        assertEquals(reason, exception.reason());
    }

    private static void createSymbolicLinkOrAbort(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable to this test process.");
        }
    }
}
