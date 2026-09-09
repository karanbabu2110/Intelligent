package io.kaos.tool.httpget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Performs one approved, non-redirecting, bounded HTTPS GET. */
public final class HttpGetExecutor {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpGetPermissionValidator validator;
    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpGetExecutor(HttpGetPermissionValidator validator) {
        this(validator, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build(), REQUEST_TIMEOUT);
    }

    HttpGetExecutor(HttpGetPermissionValidator validator, HttpClient client,
            Duration requestTimeout) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.client = Objects.requireNonNull(client, "client");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("HTTP GET timeout must be positive.");
        }
    }

    public HttpGetResult execute(HttpGetApproval.Grant grant) {
        Objects.requireNonNull(grant, "grant");
        HttpGetTarget target = grant.claim();
        rejectInterruption();
        validator.validateResolvedDestination(target);
        HttpRequest request = HttpRequest.newBuilder(target.uri())
                .timeout(requestTimeout)
                .header("Accept", "text/plain, text/html, application/json, application/xml")
                .GET().build();
        try {
            HttpResponse<InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    throw failure(HttpGetException.Reason.REDIRECTED);
                }
                if (response.statusCode() != 200) {
                    throw failure(HttpGetException.Reason.REQUEST_FAILED);
                }
                String mediaType = supportedMediaType(response);
                long declared = declaredLength(response);
                if (declared > HttpGetResult.MAX_CONTENT_UTF8_BYTES) {
                    throw failure(HttpGetException.Reason.TOO_LARGE);
                }
                byte[] bytes = readBounded(body);
                String content = decodeUtf8(bytes);
                try {
                    return new HttpGetResult(target.request(), content, mediaType);
                } catch (IllegalArgumentException exception) {
                    throw failure(HttpGetException.Reason.INVALID_CONTENT);
                }
            }
        } catch (HttpGetException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure(HttpGetException.Reason.INTERRUPTED);
        } catch (HttpTimeoutException exception) {
            throw failure(HttpGetException.Reason.TIMEOUT);
        } catch (IOException | SecurityException exception) {
            throw failure(HttpGetException.Reason.UNAVAILABLE);
        }
    }

    private static String supportedMediaType(HttpResponse<?> response) {
        String value = response.headers().firstValue("Content-Type").orElse("");
        String[] sections = value.split(";", -1);
        String mediaType = sections[0].strip().toLowerCase(Locale.ROOT);
        boolean supported = mediaType.startsWith("text/")
                || "application/json".equals(mediaType)
                || "application/xml".equals(mediaType);
        for (int index = 1; index < sections.length; index++) {
            String parameter = sections[index].strip().toLowerCase(Locale.ROOT);
            if (parameter.startsWith("charset")
                    && !"charset=utf-8".equals(parameter)
                    && !"charset=\"utf-8\"".equals(parameter)) {
                supported = false;
            }
        }
        if (!supported) throw failure(HttpGetException.Reason.UNSUPPORTED_MEDIA_TYPE);
        return mediaType;
    }

    private static long declaredLength(HttpResponse<?> response) {
        Optional<String> value = response.headers().firstValue("Content-Length");
        if (value.isEmpty()) return -1L;
        try {
            long length = Long.parseLong(value.orElseThrow());
            if (length < 0) throw new NumberFormatException("negative length");
            return length;
        } catch (NumberFormatException exception) {
            throw failure(HttpGetException.Reason.REQUEST_FAILED);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1_024];
        int total = 0;
        while (total <= HttpGetResult.MAX_CONTENT_UTF8_BYTES) {
            rejectInterruption();
            int remaining = HttpGetResult.MAX_CONTENT_UTF8_BYTES + 1 - total;
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count < 0) break;
            output.write(buffer, 0, count);
            total += count;
        }
        if (total > HttpGetResult.MAX_CONTENT_UTF8_BYTES) {
            throw failure(HttpGetException.Reason.TOO_LARGE);
        }
        return output.toByteArray();
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw failure(HttpGetException.Reason.INVALID_UTF8);
        }
    }

    private static void rejectInterruption() {
        if (Thread.currentThread().isInterrupted()) {
            throw failure(HttpGetException.Reason.INTERRUPTED);
        }
    }

    private static HttpGetException failure(HttpGetException.Reason reason) {
        return new HttpGetException(reason);
    }
}
