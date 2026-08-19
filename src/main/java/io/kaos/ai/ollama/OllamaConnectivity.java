package io.kaos.ai.ollama;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Performs the current one-shot reachability check against local Ollama. */
public final class OllamaConnectivity {
    public static final URI LOCAL_VERSION_ENDPOINT =
            URI.create("http://127.0.0.1:11434/api/version");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Pattern VERSION_RESPONSE = Pattern.compile(
            "\\s*\\{\\s*\"version\"\\s*:\\s*"
                    + "\"([A-Za-z0-9][A-Za-z0-9._+-]{0,63})\"\\s*}\\s*");

    private final HttpClient httpClient;
    private final URI versionEndpoint;
    private final Duration requestTimeout;

    /** Creates connectivity for the fixed local Ollama endpoint. */
    public OllamaConnectivity() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                LOCAL_VERSION_ENDPOINT,
                REQUEST_TIMEOUT);
    }

    OllamaConnectivity(HttpClient httpClient, URI versionEndpoint, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.versionEndpoint = requireLoopbackHttpEndpoint(versionEndpoint);
        this.requestTimeout = requirePositiveTimeout(requestTimeout);
    }

    /**
     * Checks whether the local Ollama version endpoint returns a safe version.
     *
     * @return a bounded result containing no raw response or exception detail
     */
    public Result check() {
        HttpRequest request = HttpRequest.newBuilder(versionEndpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Result.failed(Status.UNAVAILABLE);
            }

            Matcher version = VERSION_RESPONSE.matcher(response.body());
            if (!version.matches()) {
                return Result.failed(Status.INVALID_RESPONSE);
            }
            return Result.reachable(version.group(1));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failed(Status.INTERRUPTED);
        } catch (IOException | SecurityException exception) {
            return Result.failed(Status.UNAVAILABLE);
        }
    }

    private static URI requireLoopbackHttpEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "versionEndpoint");
        String host = endpoint.getHost();
        boolean loopbackHost = "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host);
        if (!"http".equalsIgnoreCase(endpoint.getScheme()) || !loopbackHost) {
            throw new IllegalArgumentException("Ollama version endpoint must use local loopback HTTP.");
        }
        return endpoint;
    }

    private static Duration requirePositiveTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "requestTimeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Ollama request timeout must be positive.");
        }
        return timeout;
    }

    /** Safe outcome of one Ollama connectivity check. */
    public record Result(Status status, String version) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(version, "version");
            if (status == Status.REACHABLE && version.isBlank()) {
                throw new IllegalArgumentException("Reachable Ollama result requires a version.");
            }
            if (status != Status.REACHABLE && !version.isEmpty()) {
                throw new IllegalArgumentException("Failed Ollama result must not contain a version.");
            }
        }

        static Result reachable(String version) {
            return new Result(Status.REACHABLE, version);
        }

        static Result failed(Status status) {
            return new Result(status, "");
        }

        public boolean reachable() {
            return status == Status.REACHABLE;
        }
    }

    /** Current minimal result categories; later features may refine failure handling. */
    public enum Status {
        REACHABLE,
        UNAVAILABLE,
        INVALID_RESPONSE,
        INTERRUPTED
    }
}
