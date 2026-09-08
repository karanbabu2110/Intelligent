package io.kaos.tool;

import java.util.Objects;
import java.util.Optional;

/** Observable result of one local-file approval decision. */
public final class ReadLocalFileApprovalOutcome {
    public enum Status {
        APPROVED,
        DENIED,
        CANCELLED,
        INVALID_RESPONSE,
        END_OF_INPUT
    }

    private final Status status;
    private final ReadLocalFileApprovalGrant grant;

    private ReadLocalFileApprovalOutcome(
            Status status, ReadLocalFileApprovalGrant grant) {
        this.status = Objects.requireNonNull(status, "status");
        this.grant = grant;
        if ((status == Status.APPROVED) != (grant != null)) {
            throw new IllegalArgumentException("Only an approved outcome may contain a grant.");
        }
    }

    static ReadLocalFileApprovalOutcome approved(ReadLocalFileApprovalGrant grant) {
        return new ReadLocalFileApprovalOutcome(
                Status.APPROVED, Objects.requireNonNull(grant, "grant"));
    }

    static ReadLocalFileApprovalOutcome notApproved(Status status) {
        if (status == Status.APPROVED) {
            throw new IllegalArgumentException("An approved outcome requires a grant.");
        }
        return new ReadLocalFileApprovalOutcome(status, null);
    }

    public Status status() {
        return status;
    }

    public boolean approved() {
        return status == Status.APPROVED;
    }

    /** Returns the single-use grant only when the exact approval token was supplied. */
    public Optional<ReadLocalFileApprovalGrant> grant() {
        return Optional.ofNullable(grant);
    }

    @Override
    public String toString() {
        return "ReadLocalFileApprovalOutcome[status=" + status + "]";
    }
}
