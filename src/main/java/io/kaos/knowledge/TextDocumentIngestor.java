package io.kaos.knowledge;

import io.kaos.knowledge.KnowledgeIngestionException.Reason;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;

/** Admits one bounded local UTF-8 plain-text document. */
public final class TextDocumentIngestor {
    public static final int MAX_DOCUMENT_BYTES = 1_048_576;
    public static final String MEDIA_TYPE = "text/plain; charset=utf-8";
    private static final int READ_BUFFER_BYTES = 8_192;

    public IngestedDocument ingest(Path path) throws KnowledgeIngestionException {
        Objects.requireNonNull(path, "path");
        String fileName = validateFileName(path);
        validateAttributes(path);
        byte[] content = readBounded(path);
        validateUtf8(content);
        return new IngestedDocument(fileName, MEDIA_TYPE, content);
    }

    private static String validateFileName(Path path) throws KnowledgeIngestionException {
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            throw failure(Reason.INVALID_DOCUMENT);
        }
        String fileName = fileNamePath.toString();
        boolean unsafeDisplayName = fileName.isBlank()
                || fileName.codePoints().anyMatch(Character::isISOControl);
        boolean unsupportedType = fileName.length() <= ".txt".length()
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".txt");
        if (unsafeDisplayName || unsupportedType) {
            throw failure(Reason.INVALID_DOCUMENT);
        }
        return fileName;
    }

    private static void validateAttributes(Path path) throws KnowledgeIngestionException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isRegularFile()
                    || attributes.size() == 0) {
                throw failure(Reason.INVALID_DOCUMENT);
            }
            if (attributes.size() > MAX_DOCUMENT_BYTES) {
                throw failure(Reason.TOO_LARGE);
            }
        } catch (NoSuchFileException exception) {
            throw failure(Reason.UNAVAILABLE);
        } catch (IOException | SecurityException exception) {
            throw failure(Reason.UNAVAILABLE);
        }
    }

    private static byte[] readBounded(Path path) throws KnowledgeIngestionException {
        OpenOption[] options = {StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS};
        try (InputStream input = Files.newInputStream(path, options);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            int total = 0;
            while (total < MAX_DOCUMENT_BYTES) {
                int count = input.read(
                        buffer, 0, Math.min(buffer.length, MAX_DOCUMENT_BYTES - total));
                if (count < 0) {
                    break;
                }
                output.write(buffer, 0, count);
                total += count;
            }
            if (input.read() >= 0) {
                throw failure(Reason.TOO_LARGE);
            }
            byte[] content = output.toByteArray();
            if (content.length == 0) {
                throw failure(Reason.INVALID_DOCUMENT);
            }
            return content;
        } catch (KnowledgeIngestionException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(Reason.UNAVAILABLE);
        }
    }

    private static void validateUtf8(byte[] content) throws KnowledgeIngestionException {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw failure(Reason.INVALID_DOCUMENT);
        }
    }

    private static KnowledgeIngestionException failure(Reason reason) {
        return new KnowledgeIngestionException(reason);
    }
}
