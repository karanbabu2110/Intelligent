package io.kaos.tool.httpget;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.KaosTool;
import io.kaos.tool.ToolDescriptor;
import io.kaos.tool.ToolSelectionException;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Runtime adapter retaining the exact host, DNS, redirect and response policies. */
public final class HttpGet implements KaosTool<HttpGetRequest> {
    private final Supplier<HttpGetPermissionValidator> configuration;
    private final BiFunction<HttpGetPermissionValidator, HttpGetApproval.Grant, HttpGetResult> execution;
    public HttpGet(Supplier<HttpGetPermissionValidator> configuration) {
        this(configuration, (validator, grant) -> new HttpGetExecutor(validator).execute(grant));
    }
    public HttpGet(Supplier<HttpGetPermissionValidator> configuration,
            BiFunction<HttpGetPermissionValidator, HttpGetApproval.Grant, HttpGetResult> execution) {
        this.configuration = Objects.requireNonNull(configuration);
        this.execution = Objects.requireNonNull(execution);
    }
    @Override public ToolDescriptor descriptor() {
        return new ToolDescriptor(HttpGetToolContract.NAME, "Retrieve one allowed HTTPS resource", true);
    }
    @Override public JsonNode definition() { return HttpGetToolContract.definition(); }
    @Override public HttpGetRequest decodeArguments(JsonNode arguments) {
        try { return HttpGetToolContract.decodeArguments(arguments); }
        catch (IllegalArgumentException exception) {
            throw new ToolSelectionException(ToolSelectionException.Reason.MALFORMED_ARGUMENTS);
        }
    }
    @Override public boolean configured() {
        try { return configuration.get() != null; }
        catch (HttpGetException exception) { return false; }
    }
    @Override public ToolPermissionPolicy<HttpGetResult> prepare(HttpGetRequest request) {
        var validator = configuration.get();
        return permission(validator, validator.validate(request), execution);
    }
    public static ToolPermissionPolicy<HttpGetResult> permission(HttpGetPermissionValidator validator,
            HttpGetTarget target,
            BiFunction<HttpGetPermissionValidator, HttpGetApproval.Grant, HttpGetResult> execution) {
        var approval = new HttpGetApproval(target);
        return new ToolPermissionPolicy<>(HttpGetToolContract.NAME, approval.prompt(), response -> {
            var outcome = approval.decide(response);
            return new ToolPermissionPolicy.Authorization<>(
                    ToolPermissionDecision.valueOf(outcome.status().name()),
                    outcome.grant().map(grant -> () -> execution.apply(validator, grant)));
        }, exception -> exception instanceof HttpGetException failure
                && failure.reason() == HttpGetException.Reason.INTERRUPTED, HttpGet::notApprovedMessage);
    }
    private static String notApprovedMessage(ToolPermissionDecision status) {
        return switch (status) {
            case DENIED -> "Tool request denied. No external request was made.";
            case CANCELLED -> "Tool request cancelled. No external request was made.";
            case INVALID_RESPONSE -> "Tool request not approved; no external request was made.";
            case END_OF_INPUT -> "Tool request ended without approval; no external request was made.";
            case APPROVED -> throw new IllegalStateException("Approved request must execute.");
        };
    }
}
