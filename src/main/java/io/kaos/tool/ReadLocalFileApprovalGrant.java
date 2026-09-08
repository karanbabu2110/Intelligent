package io.kaos.tool;

import java.util.Objects;

/** One claimable authorization for one attempt against one approved target. */
public final class ReadLocalFileApprovalGrant {
    private ReadLocalFileTarget target;
    private boolean claimed;

    ReadLocalFileApprovalGrant(ReadLocalFileTarget target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    /** Claims the approved target once for revalidation and execution by the later executor. */
    public synchronized ReadLocalFileTarget claimTarget() {
        if (claimed) {
            throw new IllegalStateException("Approval grant has already been claimed.");
        }
        claimed = true;
        ReadLocalFileTarget claimedTarget = target;
        target = null;
        return claimedTarget;
    }

    @Override
    public synchronized String toString() {
        return "ReadLocalFileApprovalGrant[target=REDACTED, claimed=" + claimed + "]";
    }
}
