package io.kaos.tool;

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
        return "Tool: read_local_file\n"
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

        if (response == null) {
            return ReadLocalFileApprovalOutcome.notApproved(
                    ReadLocalFileApprovalOutcome.Status.END_OF_INPUT);
        }
        String normalized = response.strip();
        if (APPROVE_RESPONSE.equals(normalized)) {
            return ReadLocalFileApprovalOutcome.approved(
                    new ReadLocalFileApprovalGrant(target));
        }
        if (DENY_RESPONSE.equals(normalized)) {
            return ReadLocalFileApprovalOutcome.notApproved(
                    ReadLocalFileApprovalOutcome.Status.DENIED);
        }
        return ReadLocalFileApprovalOutcome.notApproved(
                ReadLocalFileApprovalOutcome.Status.INVALID_RESPONSE);
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
