package io.kaos.app;

import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.tool.httpget.HttpGetApproval;
import io.kaos.tool.httpget.HttpGetAudit;
import io.kaos.tool.httpget.HttpGetException;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Runs one foreground, explicitly approved {@code http_get} interaction. */
final class HttpGetCommand {
    private static final int MAX_APPROVAL_CHARACTERS = 32;
    private static final String TOOL_USE_INSTRUCTION =
            "When the user asks to retrieve or summarize a URL, use http_get. "
                    + "Do not claim web access is unavailable before considering this tool. "
                    + "Otherwise answer directly.";

    private final CommandContext context;
    private final Supplier<OllamaModelConfiguration> modelLoader;
    private final HttpGetPromptSubmission promptSubmission;
    private final Supplier<HttpGetPermissionValidator> validatorLoader;
    private final BiFunction<HttpGetPermissionValidator, HttpGetApproval.Grant,
            HttpGetResult> execution;

    HttpGetCommand(CommandContext context,
            Supplier<OllamaModelConfiguration> modelLoader,
            HttpGetPromptSubmission promptSubmission,
            Supplier<HttpGetPermissionValidator> validatorLoader,
            BiFunction<HttpGetPermissionValidator, HttpGetApproval.Grant,
                    HttpGetResult> execution) {
        this.context = Objects.requireNonNull(context, "context");
        this.modelLoader = Objects.requireNonNull(modelLoader, "modelLoader");
        this.promptSubmission = Objects.requireNonNull(promptSubmission, "promptSubmission");
        this.validatorLoader = Objects.requireNonNull(validatorLoader, "validatorLoader");
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    int execute(String question) {
        OllamaPrompt prompt;
        try {
            prompt = new OllamaPrompt(question, TOOL_USE_INSTRUCTION);
        } catch (IllegalArgumentException exception) {
            context.errorOutput().println(
                    "Expected one valid quoted web question. Run 'kaos help' for usage.");
            return KaosApplication.USAGE_ERROR;
        }
        OllamaModelConfiguration model;
        try {
            model = modelLoader.get();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            report("KAOS-HTTP-GET-CONFIGURATION",
                    "HTTP GET could not load the local Ollama configuration. "
                            + "Check process settings and retry.");
            return KaosApplication.APPLICATION_ERROR;
        }
        OllamaPromptClient.Result initial = promptSubmission.request(model, prompt);
        if (!initial.successful()) return providerFailure(initial.status());
        if (!initial.toolRequested()) {
            context.output().println(initial.response());
            return KaosApplication.SUCCESS;
        }
        if (!initial.httpGetRequested()) {
            report("KAOS-HTTP-GET-INVALID-REQUEST",
                    "The model returned an unsupported tool request. Make a new request.");
            return KaosApplication.APPLICATION_ERROR;
        }
        HttpGetRequest request = initial.httpGetRequest().orElseThrow();
        try {
            HttpGetPermissionValidator validator = validatorLoader.get();
            var target = validator.validate(request);
            return approveExecuteContinue(model, prompt, initial, validator, target);
        } catch (HttpGetException | IllegalArgumentException exception) {
            reportFailure(exception instanceof HttpGetException classified
                    ? classified.reason() : HttpGetException.Reason.INVALID_REQUEST);
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private int approveExecuteContinue(OllamaModelConfiguration model, OllamaPrompt prompt,
            OllamaPromptClient.Result initial, HttpGetPermissionValidator validator,
            io.kaos.tool.httpget.HttpGetTarget target) {
        HttpGetApproval approvalRequest = new HttpGetApproval(target);
        HttpGetAudit audit = new HttpGetAudit();
        context.output().println(approvalRequest.prompt());
        HttpGetApproval.Outcome approval;
        try {
            approval = Thread.currentThread().isInterrupted()
                    ? approvalRequest.cancel() : approvalRequest.decide(readApproval());
        } catch (IOException exception) {
            approval = approvalRequest.cancel();
        }
        if (!approval.approved()) {
            context.output().println(notApprovedMessage(approval.status()));
            reportAudit(audit.notExecuted(approval));
            return KaosApplication.SUCCESS;
        }
        HttpGetResult result;
        try {
            result = execution.apply(validator, approval.grant().orElseThrow());
        } catch (HttpGetException exception) {
            reportFailure(exception.reason());
            reportAudit(exception.reason() == HttpGetException.Reason.INTERRUPTED
                    ? audit.cancelled() : audit.failed());
            return KaosApplication.APPLICATION_ERROR;
        }
        OllamaPromptClient.Result completion = promptSubmission.continueWithResult(
                model, prompt, initial, result);
        if (!completion.successful()) {
            reportAudit(audit.succeeded());
            return providerFailure(completion.status());
        }
        if (completion.toolRequested()) {
            report("KAOS-HTTP-GET-INVALID-STATE",
                    "The model requested another tool after HTTP GET; no tool was executed.");
            reportAudit(audit.succeeded());
            return KaosApplication.APPLICATION_ERROR;
        }
        context.output().println(completion.response());
        reportAudit(audit.succeeded());
        return KaosApplication.SUCCESS;
    }

    private String readApproval() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.input(), StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)));
        StringBuilder response = new StringBuilder();
        int character;
        while ((character = reader.read()) >= 0 && character != '\n' && character != '\r') {
            if (response.length() == MAX_APPROVAL_CHARACTERS) return "";
            response.append((char) character);
        }
        return character < 0 && response.isEmpty() ? null : response.toString();
    }

    private int providerFailure(OllamaPromptClient.Status status) {
        String message = switch (status) {
            case UNAVAILABLE -> "Local Ollama could not be reached. Start Ollama and retry.";
            case TOTAL_TIMEOUT, INACTIVITY_TIMEOUT ->
                    "The tool-aware local Ollama request timed out. Retry when ready.";
            case INTERRUPTED -> "The tool-aware local Ollama request was cancelled.";
            case TOKEN_LIMIT_REACHED, LOCAL_LIMIT_REACHED ->
                    "The tool-aware request exceeded a configured response boundary.";
            case REQUEST_FAILED, INVALID_RESPONSE, STREAM_FAILED ->
                    "Local Ollama could not complete the HTTP GET tool interaction.";
            case SUCCESS -> throw new IllegalStateException("Success has no provider failure.");
        };
        report("KAOS-HTTP-GET-OLLAMA", message);
        return KaosApplication.APPLICATION_ERROR;
    }

    private void reportFailure(HttpGetException.Reason reason) {
        String message = switch (reason) {
            case INVALID_CONFIGURATION -> "HTTP GET allowed-host configuration is missing or invalid.";
            case INVALID_REQUEST -> "The model requested an invalid HTTPS URL.";
            case DISALLOWED_HOST -> "The requested URL host is not explicitly allowed.";
            case NON_PUBLIC_DESTINATION -> "The requested host did not resolve only to public addresses.";
            case TIMEOUT -> "The approved HTTP GET timed out; make a new request to retry.";
            case INTERRUPTED -> "The approved HTTP GET was cancelled.";
            case REDIRECTED -> "The approved HTTP GET returned a redirect, which is not followed.";
            case REQUEST_FAILED -> "The approved HTTP GET did not return a successful response.";
            case UNSUPPORTED_MEDIA_TYPE -> "The response was not a supported textual media type.";
            case TOO_LARGE -> "The response exceeded the 32768-byte HTTP GET limit.";
            case INVALID_UTF8, INVALID_CONTENT -> "The response was not safe bounded UTF-8 text.";
            case UNAVAILABLE -> "The approved HTTP resource could not be reached.";
            case APPROVAL_REUSED -> "The single-use HTTP GET approval was already consumed.";
        };
        report("KAOS-HTTP-GET-" + reason.name().replace('_', '-'), message);
    }

    private void report(String code, String message) {
        ErrorReporter.report(context.errorOutput(), code, message);
    }

    private void reportAudit(HttpGetAudit.Record record) {
        context.output().println("AUDIT [" + record.operation() + "] target="
                + record.targetIdentity() + " decision=" + record.decision()
                + " outcome=" + record.outcome());
    }

    private static String notApprovedMessage(HttpGetApproval.Status status) {
        return switch (status) {
            case DENIED -> "Tool request denied. No external request was made.";
            case CANCELLED -> "Tool request cancelled. No external request was made.";
            case INVALID_RESPONSE -> "Tool request not approved; no external request was made.";
            case END_OF_INPUT -> "Tool request ended without approval; no external request was made.";
            case APPROVED -> throw new IllegalStateException("Approved request must execute.");
        };
    }
}
