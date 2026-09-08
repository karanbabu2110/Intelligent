package io.kaos.tool;

import io.kaos.tool.ReadLocalFileExecutionException.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Consumes one approved attempt and returns one complete bounded local-file result. */
public final class ReadLocalFileExecutor {
    private static final int READ_BUFFER_BYTES = 512;

    private final ReadLocalFilePermissionValidator validator;

    public ReadLocalFileExecutor(ReadLocalFilePermissionValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /** Claims, revalidates, reads, and decodes one approved target without partial output. */
    public ReadLocalFileResult execute(ReadLocalFileApprovalGrant grant) {
        Objects.requireNonNull(grant, "grant");
        ReadLocalFileTarget approvedTarget = grant.claimTarget();
        rejectInterruption();
        ReadLocalFileTarget beforeRead = validator.revalidate(approvedTarget);
        byte[] bytes = readBounded(beforeRead);
        rejectInterruption();
        ReadLocalFileTarget afterRead = validator.revalidate(beforeRead);
        if (bytes.length != afterRead.byteCount()) {
            throw failure(Reason.CHANGED);
        }
        String content = decodeUtf8(bytes);
        try {
            return new ReadLocalFileResult(afterRead.request(), content);
        } catch (IllegalArgumentException exception) {
            throw failure(Reason.INVALID_CONTENT);
        }
    }

    private static byte[] readBounded(ReadLocalFileTarget target) {
        OpenOption[] options = {StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS};
        try (InputStream input = Files.newInputStream(target.resolvedPath(), options);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int total = 0;
            while (total < ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES) {
                rejectInterruption();
                int count = input.read(buffer, 0, Math.min(
                        buffer.length, ReadLocalFileResult.MAX_CONTENT_UTF8_BYTES - total));
                if (count < 0) {
                    break;
                }
                output.write(buffer, 0, count);
                total += count;
            }
            if (input.read() >= 0) {
                throw failure(Reason.CHANGED);
            }
            return output.toByteArray();
        } catch (ReadLocalFileExecutionException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(Reason.UNAVAILABLE);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw failure(Reason.INVALID_UTF8);
        }
    }

    private static void rejectInterruption() {
        if (Thread.currentThread().isInterrupted()) {
            throw failure(Reason.CANCELLED);
        }
    }

    private static ReadLocalFileExecutionException failure(Reason reason) {
        return new ReadLocalFileExecutionException(reason);
    }

    @Override
    public String toString() {
        return "ReadLocalFileExecutor[validator=REDACTED]";
    }
}
