package io.kaos.ai.ollama;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

/** Test-only access to a bounded Ollama client targeting an ephemeral loopback server. */
public final class OllamaPromptClientTestSupport {
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(250);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private OllamaPromptClientTestSupport() {
    }

    /** Creates a real client without widening the production endpoint API. */
    public static OllamaPromptClient client(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        return new OllamaPromptClient(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                endpoint,
                REQUEST_TIMEOUT,
                REQUEST_TIMEOUT);
    }
}
