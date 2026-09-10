package io.kaos.tool.readlocalfile;

import io.kaos.tool.permission.ToolPermissionDecision;
import java.util.Objects;

/** One explicit approval decision bound to one metadata-validated local-file target. */
public final class ReadLocalFileApprovalRequest {
    public static final String APPROVE_RESPONSE = "approve";
    public static final String DENY_RESPONSE = "deny";

    private final ReadLocalFileTarget target;
    private boolean decided;

    public ReadLocalFileApprovalRequest(ReadLocalFileTarget target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    /**
     * Returns the intentionally path-revealing text that must be shown only to the local user.
     */
    public String prompt() {
        return "Tool: " + ReadLocalFileToolContract.NAME + "\n"
                + "Exact file: " + target.resolvedPath() + "\n"
                + "Size: " + target.byteCount() + " bytes\n"
                + "If approved, this file's text will be supplied to the configured local "
                + "Ollama model for the current answer only.\n"
                + "Type 'approve' to authorize one read attempt or 'deny' to cancel tool use.";
    }

    /** Resolves this request once; every response other than the two documented tokens fails closed. */
    public synchronized ReadLocalFileApprovalOutcome decide(String response) {
        requirePending();
        decided = true;

        var status = ToolPermissionDecision.parse(response);
        return status == ToolPermissionDecision.APPROVED
                ? ReadLocalFileApprovalOutcome.approved(new ReadLocalFileApprovalGrant(target))
                : ReadLocalFileApprovalOutcome.notApproved(
                        ReadLocalFileApprovalOutcome.Status.valueOf(status.name()));
    }

    /** Cancels this pending request without creating authority to read. */
    public synchronized ReadLocalFileApprovalOutcome cancel() {
        requirePending();
        decided = true;
        return ReadLocalFileApprovalOutcome.notApproved(
                ReadLocalFileApprovalOutcome.Status.CANCELLED);
    }

    private void requirePending() {
        if (decided) {
            throw new IllegalStateException("Approval request has already been decided.");
        }
    }

    @Override
    public String toString() {
        return "ReadLocalFileApprovalRequest[target=REDACTED, decided=" + decided + "]";
    }
}
