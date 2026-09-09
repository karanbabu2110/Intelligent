package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class HttpGetCommandIntegrationTest {
    private static final String URL = "https://example.com/reference";

    @Test
    void approvedRequestExecutesOnceAndContinuesToOneAnswer() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        AtomicBoolean executed = new AtomicBoolean();
        AtomicBoolean continued = new AtomicBoolean();
        HttpGetPromptSubmission submission = submission(continued);
        HttpGetCommand command = command("approve\n", output, errors, submission,
                (validator, grant) -> {
                    executed.set(true);
                    return new HttpGetResult(grant.claim().request(),
                            "bounded public response", "text/plain");
                });

        int exitCode = command.execute("Summarize the reference.");

        String standard = output.toString(StandardCharsets.UTF_8);
        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertTrue(executed.get());
        assertTrue(continued.get());
        assertTrue(standard.contains("Tool: http_get"));
        assertTrue(standard.contains("Exact URL: " + URL));
        assertTrue(standard.contains("The resource is bounded."));
        assertTrue(standard.matches("(?s).*AUDIT \\[http_get] target="
                + "[0-9a-f-]{36} decision=APPROVED outcome=SUCCEEDED\\R"));
        assertFalse(standard.contains("bounded public response"));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void denialDoesNotExecuteOrContinue() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicBoolean executed = new AtomicBoolean();
        AtomicBoolean continued = new AtomicBoolean();
        HttpGetCommand command = command("deny\n", output, new ByteArrayOutputStream(),
                submission(continued), (validator, grant) -> {
                    executed.set(true);
                    throw new AssertionError("Denied request must not execute.");
                });

        assertEquals(KaosApplication.SUCCESS,
                command.execute("Summarize the reference."));
        assertFalse(executed.get());
        assertFalse(continued.get());
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("decision=DENIED outcome=NOT_EXECUTED"));
    }

    @Test
    void invalidApprovalAndEndOfInputDoNotExecuteOrContinue() {
        for (String input : new String[] {"yes\n", "APPROVE\n", "approve twice\n", ""}) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            HttpGetCommand command = command(input, output, errors,
                    requestOnly(new HttpGetRequest(URL)), (validator, grant) -> {
                        throw new AssertionError("Unapproved request must not execute.");
                    });

            assertEquals(KaosApplication.SUCCESS,
                    command.execute("Summarize the reference."));
            String decision = input.isEmpty() ? "END_OF_INPUT" : "INVALID_RESPONSE";
            assertTrue(output.toString(StandardCharsets.UTF_8)
                    .contains("decision=" + decision + " outcome=NOT_EXECUTED"));
            assertEquals("", errors.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void interruptedApprovalDoesNotExecuteOrContinueEvenWithApproveInput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        HttpGetCommand command = command("approve\n", output, new ByteArrayOutputStream(),
                requestOnly(new HttpGetRequest(URL)), (validator, grant) -> {
                    throw new AssertionError("Cancelled request must not execute.");
                });

        Thread.currentThread().interrupt();
        try {
            assertEquals(KaosApplication.SUCCESS,
                    command.execute("Summarize the reference."));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(output.toString(StandardCharsets.UTF_8)
                    .contains("decision=CANCELLED outcome=NOT_EXECUTED"));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void disallowedHostFailsBeforeApprovalOrExecution() {
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        HttpGetPromptSubmission submission = requestOnly(
                new HttpGetRequest("https://other.example/private"));
        HttpGetCommand command = command("approve\n", new ByteArrayOutputStream(), errors,
                submission, (validator, grant) -> {
                    throw new AssertionError("Disallowed host must not execute.");
                });

        assertEquals(KaosApplication.APPLICATION_ERROR,
                command.execute("Read the private URL."));
        assertTrue(errors.toString(StandardCharsets.UTF_8)
                .contains("KAOS-HTTP-GET-DISALLOWED-HOST"));
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains("other.example"));
    }

    private static HttpGetCommand command(String input, ByteArrayOutputStream output,
            ByteArrayOutputStream errors, HttpGetPromptSubmission submission,
            java.util.function.BiFunction<HttpGetPermissionValidator,
                    io.kaos.tool.httpget.HttpGetApproval.Grant, HttpGetResult> execution) {
        CommandContext context = new CommandContext(new ApplicationConfiguration("KAOS"),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        return new HttpGetCommand(context, () -> new OllamaModelConfiguration("qwen3"),
                submission, () -> new HttpGetPermissionValidator(Set.of("example.com")),
                execution);
    }

    private static HttpGetPromptSubmission submission(AtomicBoolean continued) {
        return new HttpGetPromptSubmission() {
            @Override
            public OllamaPromptClient.Result request(
                    OllamaModelConfiguration model, OllamaPrompt prompt) {
                assertEquals("Summarize the reference.", prompt.text());
                assertTrue(prompt.systemInstruction().contains("use http_get"));
                assertTrue(prompt.systemInstruction().contains(
                        "Do not claim web access is unavailable"));
                return pending(new HttpGetRequest(URL));
            }

            @Override
            public OllamaPromptClient.Result continueWithResult(
                    OllamaModelConfiguration model, OllamaPrompt prompt,
                    OllamaPromptClient.Result toolCallResult, HttpGetResult result) {
                continued.set(true);
                assertEquals("bounded public response", result.content());
                return answer("The resource is bounded.");
            }
        };
    }

    private static HttpGetPromptSubmission requestOnly(HttpGetRequest request) {
        return new HttpGetPromptSubmission() {
            @Override
            public OllamaPromptClient.Result request(
                    OllamaModelConfiguration model, OllamaPrompt prompt) {
                return pending(request);
            }

            @Override
            public OllamaPromptClient.Result continueWithResult(
                    OllamaModelConfiguration model, OllamaPrompt prompt,
                    OllamaPromptClient.Result toolCallResult, HttpGetResult result) {
                throw new AssertionError("Continuation must not run.");
            }
        };
    }

    private static OllamaPromptClient.Result pending(HttpGetRequest request) {
        return new OllamaPromptClient.Result(OllamaPromptClient.Status.SUCCESS, "", "",
                Optional.empty(), Optional.of(request),
                OllamaPromptClient.CompletionReason.STOP,
                new OllamaPromptClient.CompletionMetrics(1, 1, 1, 1));
    }

    private static OllamaPromptClient.Result answer(String text) {
        return new OllamaPromptClient.Result(
                OllamaPromptClient.Status.SUCCESS, "", text);
    }
}
