package io.kaos.app;

import io.kaos.tool.history.ToolExecutionHistory;
import io.kaos.tool.history.ToolHistoryStorageException;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Objects;
import java.util.function.Supplier;

/** Writes one safe terminal snapshot and reports content-free storage failures. */
final class ToolHistoryRecorder {
    private final CommandContext context;
    private final Supplier<ToolExecutionHistory> historyLoader;

    ToolHistoryRecorder(CommandContext context, Supplier<ToolExecutionHistory> historyLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.historyLoader = Objects.requireNonNull(historyLoader, "historyLoader");
    }

    boolean record(ToolPermissionPolicy.Snapshot snapshot) {
        final ToolExecutionHistory history;
        try {
            history = historyLoader.get();
        } catch (IllegalArgumentException | ToolHistoryStorageException exception) {
            reportFailure();
            return false;
        }
        try {
            history.record(snapshot);
            return true;
        } catch (ToolHistoryStorageException exception) {
            reportFailure();
            return false;
        }
    }

    private void reportFailure() {
        ErrorReporter.report(context.errorOutput(), "KAOS-TOOL-HISTORY-002",
                "The tool attempt finished, but its local history record could not be saved. "
                        + "Check the tool-history data directory before making another request.");
    }
}
