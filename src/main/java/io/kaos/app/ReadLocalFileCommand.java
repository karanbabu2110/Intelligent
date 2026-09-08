package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.tool.readlocalfile.ReadLocalFileApprovalOutcome;
import io.kaos.tool.readlocalfile.ReadLocalFileApprovalRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileAuditContext;
import io.kaos.tool.readlocalfile.ReadLocalFileAuditRecord;
import io.kaos.tool.readlocalfile.ReadLocalFileExecutionException;
import io.kaos.tool.readlocalfile.ReadLocalFileExecutor;
import io.kaos.tool.readlocalfile.ReadLocalFileFailure;
import io.kaos.tool.readlocalfile.ReadLocalFileFailureMapper;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionException;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/** Runs one foreground, explicitly approved {@code read_local_file} interaction. */
final class ReadLocalFileCommand {
    private static final int MAX_APPROVAL_CHARACTERS = 32;

    private final CommandContext context;
    private final Supplier<OllamaModelConfiguration> modelConfigurationLoader;
    private final ReadLocalFilePromptSubmission promptSubmission;
    private final Supplier<ReadLocalFilePermissionValidator> validatorLoader;

    ReadLocalFileCommand(
            CommandContext context,
            Supplier<OllamaModelConfiguration> modelConfigurationLoader,
            ReadLocalFilePromptSubmission promptSubmission,
            Supplier<ReadLocalFilePermissionValidator> validatorLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.modelConfigurationLoader = Objects.requireNonNull(
                modelConfigurationLoader, "modelConfigurationLoader");
        this.promptSubmission = Objects.requireNonNull(promptSubmission, "promptSubmission");
        this.validatorLoader = Objects.requireNonNull(validatorLoader, "validatorLoader");
    }

    int execute(String question) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(question);
        } catch (IllegalArgumentException exception) {
            context.errorOutput().println(
                    "Expected one valid quoted file question. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }

        OllamaModelConfiguration model;
        try {
            model = modelConfigurationLoader.get();
        } catch (IllegalArgumentException exception) {
            report(KaosApplication.INVALID_OLLAMA_MODEL_CODE,
                    "Invalid Ollama configuration. Check model, context-window, thinking, and "
                            + "response-token-limit process settings and retry.");
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalStateException exception) {
            report(KaosApplication.UNREADABLE_OLLAMA_MODEL_CODE,
                    "Ollama model configuration could not be read. Check process permissions "
                            + "and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }

        OllamaPromptClient.Result initial = promptSubmission.request(model, prompt);
        if (!initial.successful()) {
            return reportProviderFailure(initial.status());
        }
        if (!initial.toolRequested()) {
            context.output().println(initial.response());
            return KaosApplication.SUCCESS;
        }

        ReadLocalFileRequest request = initial.toolRequest().orElseThrow();
        ReadLocalFilePermissionValidator validator;
        try {
            validator = validatorLoader.get();
            var target = validator.validate(request);
            return approveExecuteAndContinue(model, prompt, initial, validator, target);
        } catch (ReadLocalFilePermissionException exception) {
            report(ReadLocalFileFailureMapper.from(exception));
            return KaosApplication.APPLICATION_ERROR;
        } catch (IllegalArgumentException exception) {
            report(ReadLocalFileFailureMapper.invalidRequest());
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private int approveExecuteAndContinue(
            OllamaModelConfiguration model,
            OllamaPrompt prompt,
            OllamaPromptClient.Result initial,
            ReadLocalFilePermissionValidator validator,
            io.kaos.tool.readlocalfile.ReadLocalFileTarget target) {
        ReadLocalFileAuditContext audit = ReadLocalFileAuditContext.start(target);
        ReadLocalFileApprovalRequest approvalRequest = new ReadLocalFileApprovalRequest(target);
        context.output().println(approvalRequest.prompt());

        ReadLocalFileApprovalOutcome approval;
        try {
            if (Thread.currentThread().isInterrupted()) {
                approval = approvalRequest.cancel();
            } else {
                approval = approvalRequest.decide(readApprovalResponse());
            }
        } catch (IOException exception) {
            approval = approvalRequest.cancel();
        }

        if (!approval.approved()) {
            ReadLocalFileAuditRecord record = audit.recordNotExecuted(approval);
            context.output().println(notApprovedMessage(approval.status()));
            reportAudit(record);
            return KaosApplication.SUCCESS;
        }

        ReadLocalFileResult result;
        try {
            result = new ReadLocalFileExecutor(validator)
                    .execute(approval.grant().orElseThrow());
        } catch (ReadLocalFileExecutionException exception) {
            ReadLocalFileAuditRecord record =
                    exception.reason() == ReadLocalFileExecutionException.Reason.CANCELLED
                            ? audit.recordExecutionCancelled()
                            : audit.recordFailed();
            report(ReadLocalFileFailureMapper.from(exception));
            reportAudit(record);
            return KaosApplication.APPLICATION_ERROR;
        } catch (ReadLocalFilePermissionException exception) {
            report(ReadLocalFileFailureMapper.from(exception));
            reportAudit(audit.recordFailed());
            return KaosApplication.APPLICATION_ERROR;
        }

        ReadLocalFileAuditRecord record = audit.recordSucceeded();
        OllamaPromptClient.Result completion =
                promptSubmission.continueWithResult(
                        model, prompt, initial, result);
        if (!completion.successful()) {
            reportAudit(record);
            return reportProviderFailure(completion.status());
        }
        if (completion.toolRequested()) {
            report(ReadLocalFileFailureMapper.invalidState());
            reportAudit(record);
            return KaosApplication.APPLICATION_ERROR;
        }
        context.output().println(completion.response());
        reportAudit(record);
        return KaosApplication.SUCCESS;
    }

    private String readApprovalResponse() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.input(), StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)));
        StringBuilder response = new StringBuilder();
        int character;
        while ((character = reader.read()) >= 0) {
            if (character == '\n' || character == '\r') {
                break;
            }
            if (response.length() == MAX_APPROVAL_CHARACTERS) {
                return "";
            }
            response.append((char) character);
        }
        return character < 0 && response.isEmpty() ? null : response.toString();
    }

    private int reportProviderFailure(OllamaPromptClient.Status status) {
        ProviderFailure failure = switch (status) {
            case UNAVAILABLE -> new ProviderFailure(
                    KaosApplication.OLLAMA_CONNECTIVITY_CODE,
                    "Local Ollama could not be reached. Start Ollama on "
                            + "127.0.0.1:11434 and make a new request.");
            case REQUEST_FAILED, INVALID_RESPONSE -> new ProviderFailure(
                    KaosApplication.OLLAMA_PROMPT_CODE,
                    "Local Ollama could not complete the tool-aware prompt. Verify the "
                            + "configured model and make a new request.");
            case TOKEN_LIMIT_REACHED, LOCAL_LIMIT_REACHED -> new ProviderFailure(
                    KaosApplication.OLLAMA_RESPONSE_LIMIT_CODE,
                    "The tool-aware prompt exceeded an Ollama or KAOS response boundary. "
                            + "Shorten the question or file and make a new request.");
            case STREAM_FAILED -> new ProviderFailure(
                    KaosApplication.OLLAMA_STREAM_CODE,
                    "The local Ollama response stream ended before completion. Verify "
                            + "Ollama is running and make a new request.");
            case TOTAL_TIMEOUT, INACTIVITY_TIMEOUT -> new ProviderFailure(
                    KaosApplication.OLLAMA_TIMEOUT_CODE,
                    "The tool-aware prompt timed out. Select a faster local model or "
                            + "shorten the request, then retry.");
            case INTERRUPTED -> new ProviderFailure(
                    KaosApplication.OLLAMA_CANCELLATION_CODE,
                    "The tool-aware prompt was cancelled. Retry when ready.");
            case SUCCESS -> throw new IllegalStateException(
                    "Successful result has no provider failure.");
        };
        report(failure.code(), failure.message());
        return KaosApplication.APPLICATION_ERROR;
    }

    private void report(ReadLocalFileFailure failure) {
        context.errorOutput().println(failure.diagnostic());
    }

    private void report(String code, String message) {
        ErrorReporter.report(context.errorOutput(), code, message);
    }

    private void reportAudit(ReadLocalFileAuditRecord record) {
        context.output().println("AUDIT [" + record.operation() + "] target="
                + record.targetIdentity() + " decision=" + record.decision()
                + " outcome=" + record.outcome());
    }

    private static String notApprovedMessage(ReadLocalFileApprovalOutcome.Status status) {
        return switch (status) {
            case DENIED -> "Tool request denied. No file was read.";
            case CANCELLED -> "Tool request cancelled. No file was read.";
            case INVALID_RESPONSE ->
                    "Tool request not approved. Expected 'approve' or 'deny'; no file was read.";
            case END_OF_INPUT -> "Tool request ended without approval. No file was read.";
            case APPROVED -> throw new IllegalStateException(
                    "Approved tool request must be executed.");
        };
    }

    private record ProviderFailure(String code, String message) { }
}
