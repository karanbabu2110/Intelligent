package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.ai.ollama.OllamaPromptClient.CompletionMetrics;
import io.kaos.ai.ollama.OllamaPromptClient.CompletionReason;
import io.kaos.ai.ollama.OllamaPromptClient.Result;
import io.kaos.ai.ollama.OllamaPromptClient.Status;
import io.kaos.app.config.ApplicationConfiguration;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadLocalFileCommandIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runsOneApprovedFileReadThroughTheFinalModelAnswer() throws Exception {
        String privateContent = "final class ApprovedFile {}";
        Files.writeString(temporaryDirectory.resolve("ApprovedFile.java"), privateContent);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        AtomicBoolean continued = new AtomicBoolean();
        ReadLocalFilePromptSubmission submission = new ReadLocalFilePromptSubmission() {
            @Override
            public Result request(OllamaModelConfiguration model, OllamaPrompt prompt) {
                assertEquals("Explain ApprovedFile.java", prompt.text());
                return toolRequest("ApprovedFile.java");
            }

            @Override
            public Result continueWithResult(
                    OllamaModelConfiguration model,
                    OllamaPrompt prompt,
                    Result toolCallResult,
                    ReadLocalFileResult result) {
                continued.set(true);
                assertEquals("ApprovedFile.java",
                        toolCallResult.toolRequest().orElseThrow().path());
                assertEquals(privateContent, result.content());
                return answer("It declares the ApprovedFile class.");
            }
        };

        var history = new RecordingToolHistory();
        int exitCode = command("approve\n", output, errors, submission)
                .withToolHistory(() -> history)
                .execute("Explain ApprovedFile.java");

        String standardOutput = output.toString(StandardCharsets.UTF_8);
        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertTrue(continued.get());
        assertTrue(standardOutput.contains("Tool: read_local_file"));
        assertTrue(standardOutput.contains(
                temporaryDirectory.resolve("ApprovedFile.java").toRealPath().toString()));
        assertTrue(standardOutput.contains("It declares the ApprovedFile class."));
        assertTrue(standardOutput.matches("(?s).*AUDIT \\[read_local_file] target="
                + "[0-9a-f-]{36} decision=APPROVED outcome=SUCCEEDED\\R"));
        assertFalse(standardOutput.contains(privateContent));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        assertEquals(io.kaos.tool.ToolExecutionOutcome.SUCCEEDED,
                history.records().getFirst().outcome());
    }

    @Test
    void denialDoesNotExecuteOrContinueTheTool() throws Exception {
        String privateContent = "never disclose this value";
        Files.writeString(temporaryDirectory.resolve("private.txt"), privateContent);
        AtomicBoolean continued = new AtomicBoolean();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ReadLocalFilePromptSubmission submission = new ReadLocalFilePromptSubmission() {
            @Override
            public Result request(OllamaModelConfiguration model, OllamaPrompt prompt) {
                return toolRequest("private.txt");
            }

            @Override
            public Result continueWithResult(
                    OllamaModelConfiguration model,
                    OllamaPrompt prompt,
                    Result toolCallResult,
                    ReadLocalFileResult result) {
                continued.set(true);
                return answer("unexpected");
            }
        };

        int exitCode = command("deny\n", output, new ByteArrayOutputStream(), submission)
                .execute("Read private.txt");

        String standardOutput = output.toString(StandardCharsets.UTF_8);
        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertFalse(continued.get());
        assertTrue(standardOutput.contains("Tool request denied. No file was read."));
        assertTrue(standardOutput.contains("decision=DENIED outcome=NOT_EXECUTED"));
        assertFalse(standardOutput.contains(privateContent));
    }

    @Test
    void directModelAnswerNeedsNeitherReadRootNorApproval() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        AtomicBoolean validatorLoaded = new AtomicBoolean();
        CommandContext context = new CommandContext(
                new ApplicationConfiguration("KAOS"),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        ReadLocalFileCommand command = new ReadLocalFileCommand(
                context,
                () -> new OllamaModelConfiguration("qwen3"),
                new ReadLocalFilePromptSubmission() {
                    @Override
                    public Result request(
                            OllamaModelConfiguration model, OllamaPrompt prompt) {
                        return answer("No file is required.");
                    }

                    @Override
                    public Result continueWithResult(
                            OllamaModelConfiguration model,
                            OllamaPrompt prompt,
                            Result toolCallResult,
                            ReadLocalFileResult result) {
                        throw new AssertionError("Tool continuation must not run.");
                    }
                },
                () -> {
                    validatorLoaded.set(true);
                    return new ReadLocalFilePermissionValidator(temporaryDirectory);
                });

        assertEquals(KaosApplication.SUCCESS,
                command.execute("Answer without a file"));
        assertEquals("No file is required." + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8));
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
        assertFalse(validatorLoaded.get());
    }

    @Test
    void unrecognizedApprovalFailsClosedWithoutExecution() throws Exception {
        Files.writeString(temporaryDirectory.resolve("safe.txt"), "private content");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicBoolean continued = new AtomicBoolean();
        ReadLocalFilePromptSubmission submission = new ReadLocalFilePromptSubmission() {
            @Override
            public Result request(OllamaModelConfiguration model, OllamaPrompt prompt) {
                return toolRequest("safe.txt");
            }

            @Override
            public Result continueWithResult(
                    OllamaModelConfiguration model,
                    OllamaPrompt prompt,
                    Result toolCallResult,
                    ReadLocalFileResult result) {
                continued.set(true);
                return answer("unexpected");
            }
        };

        assertEquals(KaosApplication.SUCCESS,
                command("yes\n", output, new ByteArrayOutputStream(), submission)
                        .execute("Explain safe.txt"));
        assertFalse(continued.get());
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("decision=INVALID_RESPONSE outcome=NOT_EXECUTED"));
    }

    @Test
    void eofAfterUnterminatedApprovalDoesNotExecuteOrContinue() throws Exception {
        Files.writeString(temporaryDirectory.resolve("safe.txt"), "private content");
        var output = new ByteArrayOutputStream();
        assertEquals(KaosApplication.SUCCESS,
                command("approve", output, new ByteArrayOutputStream(), requestOnly("safe.txt"))
                        .execute("Explain safe.txt"));
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("decision=END_OF_INPUT outcome=NOT_EXECUTED"));
    }

    @Test
    void invalidTargetProducesOnlyTheStableToolDiagnostic() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        ReadLocalFilePromptSubmission submission = requestOnly("missing.java");

        int exitCode = command("approve\n", output, errors, submission)
                .execute("Explain missing.java");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals(
                "ERROR [KAOS-TOOL-READ-003] The local file is unavailable. Verify the "
                        + "configured root and file exist and are accessible, then make a new "
                        + "request." + System.lineSeparator(),
                errors.toString(StandardCharsets.UTF_8));
        assertFalse(errors.toString(StandardCharsets.UTF_8)
                .contains(temporaryDirectory.toString()));
    }

    @Test
    void providerFailureAfterTheReadStillRecordsSuccessfulLocalExecution()
            throws Exception {
        String privateContent = "private tool result";
        Files.writeString(temporaryDirectory.resolve("result.txt"), privateContent);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        ReadLocalFilePromptSubmission submission = new ReadLocalFilePromptSubmission() {
            @Override
            public Result request(OllamaModelConfiguration model, OllamaPrompt prompt) {
                return toolRequest("result.txt");
            }

            @Override
            public Result continueWithResult(
                    OllamaModelConfiguration model,
                    OllamaPrompt prompt,
                    Result toolCallResult,
                    ReadLocalFileResult result) {
                return failed(Status.UNAVAILABLE);
            }
        };

        int exitCode = command("approve\n", output, errors, submission)
                .execute("Explain result.txt");

        assertEquals(KaosApplication.APPLICATION_ERROR, exitCode);
        assertTrue(output.toString(StandardCharsets.UTF_8)
                .contains("decision=APPROVED outcome=SUCCEEDED"));
        assertFalse(output.toString(StandardCharsets.UTF_8).contains(privateContent));
        assertTrue(errors.toString(StandardCharsets.UTF_8)
                .startsWith("ERROR [KAOS-AI-001]"));
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains(privateContent));
    }

    private ReadLocalFileCommand command(
            String input,
            ByteArrayOutputStream output,
            ByteArrayOutputStream errors,
            ReadLocalFilePromptSubmission submission) {
        CommandContext context = new CommandContext(
                new ApplicationConfiguration("KAOS"),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
        return new ReadLocalFileCommand(
                context,
                () -> new OllamaModelConfiguration("qwen3"),
                submission,
                () -> new ReadLocalFilePermissionValidator(temporaryDirectory));
    }

    private static ReadLocalFilePromptSubmission requestOnly(String path) {
        return new ReadLocalFilePromptSubmission() {
            @Override
            public Result request(OllamaModelConfiguration model, OllamaPrompt prompt) {
                return toolRequest(path);
            }

            @Override
            public Result continueWithResult(
                    OllamaModelConfiguration model,
                    OllamaPrompt prompt,
                    Result toolCallResult,
                    ReadLocalFileResult result) {
                throw new AssertionError("Tool continuation must not run.");
            }
        };
    }

    private static Result toolRequest(String path) {
        return new Result(Status.SUCCESS, "", "",
                Optional.of(new ReadLocalFileRequest(path)), CompletionReason.STOP,
                new CompletionMetrics(1, 1, 1, 1));
    }

    private static Result answer(String response) {
        return new Result(Status.SUCCESS, "", response, Optional.empty(),
                CompletionReason.STOP, new CompletionMetrics(1, 1, 1, 1));
    }

    private static Result failed(Status status) {
        return new Result(status, "", "", Optional.empty(), CompletionReason.NONE,
                new CompletionMetrics(0, 0, 0, 0));
    }
}
