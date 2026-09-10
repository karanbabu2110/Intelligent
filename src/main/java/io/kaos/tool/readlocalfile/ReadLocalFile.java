package io.kaos.tool.readlocalfile;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.KaosTool;
import io.kaos.tool.ToolDescriptor;
import io.kaos.tool.ToolSelectionException;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Objects;
import java.util.function.Supplier;

/** Runtime adapter; containment, metadata and bounded reads stay in their concrete owners. */
public final class ReadLocalFile implements KaosTool<ReadLocalFileRequest> {
    private final Supplier<ReadLocalFilePermissionValidator> configuration;
    public ReadLocalFile(Supplier<ReadLocalFilePermissionValidator> configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }
    @Override public ToolDescriptor descriptor() {
        return new ToolDescriptor(ReadLocalFileToolContract.NAME, "Read one bounded local file", true);
    }
    @Override public JsonNode definition() { return ReadLocalFileToolContract.definition(); }
    @Override public ReadLocalFileRequest decodeArguments(JsonNode arguments) {
        try { return ReadLocalFileToolContract.decodeArguments(arguments); }
        catch (IllegalArgumentException exception) {
            throw new ToolSelectionException(ToolSelectionException.Reason.MALFORMED_ARGUMENTS);
        }
    }
    @Override public boolean configured() {
        try { return configuration.get() != null; }
        catch (ReadLocalFilePermissionException exception) { return false; }
    }
    @Override public ToolPermissionPolicy<ReadLocalFileResult> prepare(ReadLocalFileRequest request) {
        var validator = configuration.get();
        return permission(validator, validator.validate(request));
    }
    public static ToolPermissionPolicy<ReadLocalFileResult> permission(
            ReadLocalFilePermissionValidator validator, ReadLocalFileTarget target) {
        var approval = new ReadLocalFileApprovalRequest(target);
        return new ToolPermissionPolicy<>(ReadLocalFileToolContract.NAME, approval.prompt(), response -> {
            var outcome = approval.decide(response);
            return new ToolPermissionPolicy.Authorization<>(
                    ToolPermissionDecision.valueOf(outcome.status().name()),
                    outcome.grant().map(grant -> () -> new ReadLocalFileExecutor(validator).execute(grant)));
        }, exception -> exception instanceof ReadLocalFileExecutionException failure
                && failure.reason() == ReadLocalFileExecutionException.Reason.CANCELLED, ReadLocalFile::notApprovedMessage);
    }
    private static String notApprovedMessage(ToolPermissionDecision status) {
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

}
