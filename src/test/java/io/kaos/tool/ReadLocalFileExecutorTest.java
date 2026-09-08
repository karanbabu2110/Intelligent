package io.kaos.tool;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kaos.tool.ReadLocalFileExecutionException.Reason;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadLocalFileExecutorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void consumesApprovalAndReturnsOneCompleteExactUtf8Result() throws Exception {
        String content = "package demo;\n\nclass Example { String greeting = \"नमस्ते\"; }\n";
        Path file = write("Example.java", content.getBytes(StandardCharsets.UTF_8));
        byte[] original = Files.readAllBytes(file);

        ReadLocalFileResult result = executor().execute(approved("Example.java"));

        assertEquals(new ReadLocalFileRequest("Example.java"), result.request());
        assertEquals(content, result.content());
        assertEquals(original.length, result.utf8ByteCount());
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void acceptsTheExactByteLimit() throws Exception {
        byte[] content = "a".repeat(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES)
                .getBytes(StandardCharsets.UTF_8);
        write("limit.txt", content);

        ReadLocalFileResult result = executor().execute(approved("limit.txt"));

        assertEquals(ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES, result.utf8ByteCount());
    }

    @Test
    void rejectsMalformedUtf8WithoutReturningPartialContent() throws Exception {
        write("malformed.txt", new byte[] {(byte) 0xc3, (byte) 0x28});

        assertFailure(() -> executor().execute(approved("malformed.txt")),
                Reason.INVALID_UTF8);
    }

    @Test
    void rejectsBlankAndUnsafeControlContent() throws Exception {
        write("blank.txt", " \n\t".getBytes(StandardCharsets.UTF_8));
        write("control.txt", "safe\u0000unsafe".getBytes(StandardCharsets.UTF_8));

        assertFailure(() -> executor().execute(approved("blank.txt")),
                Reason.INVALID_CONTENT);
        assertFailure(() -> executor().execute(approved("control.txt")),
                Reason.INVALID_CONTENT);
    }

    @Test
    void changedTargetFailsRevalidationAndConsumesTheGrant() throws Exception {
        write("changing.txt", "first".getBytes(StandardCharsets.UTF_8));
        ReadLocalFileApprovalGrant grant = approved("changing.txt");
        Files.writeString(temporaryDirectory.resolve("changing.txt"),
                "different length", StandardCharsets.UTF_8);

        ReadLocalFilePermissionException exception = assertThrows(
                ReadLocalFilePermissionException.class,
                () -> executor().execute(grant));

        assertEquals(ReadLocalFilePermissionException.Reason.CHANGED, exception.reason());
        assertThrows(IllegalStateException.class, () -> executor().execute(grant));
    }

    @Test
    void oversizedReplacementFailsBeforeReadingAndConsumesTheGrant() throws Exception {
        write("growing.txt", "small".getBytes(StandardCharsets.UTF_8));
        ReadLocalFileApprovalGrant grant = approved("growing.txt");
        Files.write(temporaryDirectory.resolve("growing.txt"),
                new byte[ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES + 1]);

        ReadLocalFilePermissionException exception = assertThrows(
                ReadLocalFilePermissionException.class,
                () -> executor().execute(grant));

        assertEquals(ReadLocalFilePermissionException.Reason.TOO_LARGE, exception.reason());
        assertThrows(IllegalStateException.class, () -> executor().execute(grant));
    }

    @Test
    void interruptedExecutionFailsBeforeReadingAndConsumesTheGrant() throws Exception {
        write("cancelled.txt", "private".getBytes(StandardCharsets.UTF_8));
        ReadLocalFileApprovalGrant grant = approved("cancelled.txt");
        Thread.currentThread().interrupt();
        try {
            assertFailure(() -> executor().execute(grant), Reason.CANCELLED);
        } finally {
            Thread.interrupted();
        }
        assertThrows(IllegalStateException.class, () -> executor().execute(grant));
    }

    @Test
    void failuresAndDiagnosticsDoNotExposePathOrContent() throws Exception {
        String privateContent = "private customer content";
        Path file = write("private.txt", privateContent.getBytes(StandardCharsets.UTF_8));
        ReadLocalFileExecutor executor = executor();
        ReadLocalFileApprovalGrant grant = approved("private.txt");
        Files.delete(file);

        ReadLocalFilePermissionException exception = assertThrows(
                ReadLocalFilePermissionException.class,
                () -> executor.execute(grant));

        assertFalse(exception.getMessage().contains(file.toString()));
        assertFalse(exception.getMessage().contains(privateContent));
        assertFalse(executor.toString().contains(temporaryDirectory.toString()));
    }

    private ReadLocalFileExecutor executor() {
        return new ReadLocalFileExecutor(
                new ReadLocalFilePermissionValidator(temporaryDirectory));
    }

    private ReadLocalFileApprovalGrant approved(String path) {
        ReadLocalFileTarget target = new ReadLocalFilePermissionValidator(temporaryDirectory)
                .validate(new ReadLocalFileRequest(path));
        return new ReadLocalFileApprovalRequest(target)
                .decide("approve")
                .grant()
                .orElseThrow();
    }

    private Path write(String name, byte[] content) throws Exception {
        return Files.write(temporaryDirectory.resolve(name), content);
    }

    private static void assertFailure(Runnable operation, Reason reason) {
        ReadLocalFileExecutionException exception = assertThrows(
                ReadLocalFileExecutionException.class, operation::run);
        assertEquals(reason, exception.reason());
        assertEquals("Approved local file tool execution failed.", exception.getMessage());
    }
}
