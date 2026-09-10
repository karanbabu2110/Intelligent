package io.kaos.tool.httpget;

import io.kaos.tool.permission.ToolPermissionDecision;
import java.util.Objects;
import java.util.Optional;

/** One exact approval decision and single-use authority for {@code http_get}. */
public final class HttpGetApproval {
    private final HttpGetTarget target;
    private boolean decided;

    public HttpGetApproval(HttpGetTarget target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    public String prompt() {
        return "Tool: " + HttpGetToolContract.NAME + "\n"
                + "Exact URL: " + target.uri() + "\n"
                + "If approved, KAOS will contact this external host and supply its bounded "
                + "text response to the configured local Ollama model for this answer only.\n"
                + "Type 'approve' to authorize one GET attempt or 'deny' to cancel tool use.";
    }

    public synchronized Outcome decide(String response) {
        requirePending();
        decided = true;
        var status = ToolPermissionDecision.parse(response);
        return status == ToolPermissionDecision.APPROVED
                ? Outcome.approved(new Grant(target))
                : Outcome.notApproved(Status.valueOf(status.name()));
    }

    public synchronized Outcome cancel() {
        requirePending();
        decided = true;
        return Outcome.notApproved(Status.CANCELLED);
    }

    private void requirePending() {
        if (decided) throw new IllegalStateException("Approval has already been decided.");
    }

    public enum Status { APPROVED, DENIED, CANCELLED, INVALID_RESPONSE, END_OF_INPUT }

    public static final class Outcome {
        private final Status status;
        private final Grant grant;

        private Outcome(Status status, Grant grant) {
            this.status = Objects.requireNonNull(status, "status");
            this.grant = grant;
        }

        static Outcome approved(Grant grant) {
            return new Outcome(Status.APPROVED, Objects.requireNonNull(grant, "grant"));
        }

        static Outcome notApproved(Status status) {
            return new Outcome(status, null);
        }

        public Status status() {
            return status;
        }

        public boolean approved() {
            return status == Status.APPROVED;
        }

        public Optional<Grant> grant() {
            return Optional.ofNullable(grant);
        }
    }

    public static final class Grant {
        private HttpGetTarget target;

        private Grant(HttpGetTarget target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        public synchronized HttpGetTarget claim() {
            if (target == null) throw new HttpGetException(HttpGetException.Reason.APPROVAL_REUSED);
            HttpGetTarget claimed = target;
            target = null;
            return claimed;
        }

        @Override
        public synchronized String toString() {
            return "HttpGetApproval.Grant[target=REDACTED, claimed=" + (target == null) + "]";
        }
    }

    @Override
    public synchronized String toString() {
        return "HttpGetApproval[target=REDACTED, decided=" + decided + "]";
    }
}
