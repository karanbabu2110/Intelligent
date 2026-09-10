package io.kaos.app;

import io.kaos.tool.history.ToolExecutionHistory;
import io.kaos.tool.history.ToolExecutionRecord;
import io.kaos.tool.history.ToolHistoryStorageException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Displays a bounded, content-free view of recent terminal tool attempts. */
final class ToolHistoryCommand {
    static final int DISPLAY_LIMIT = 20;
    private final CommandContext context;
    private final Supplier<ToolExecutionHistory> historyLoader;

    ToolHistoryCommand(CommandContext context, Supplier<ToolExecutionHistory> historyLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.historyLoader = Objects.requireNonNull(historyLoader, "historyLoader");
    }

    int execute() {
        final ToolExecutionHistory history;
        try {
            history = historyLoader.get();
        } catch (IllegalArgumentException | ToolHistoryStorageException exception) {
            return unavailable();
        }
        final List<ToolExecutionRecord> records;
        try {
            records = history.recent(DISPLAY_LIMIT);
        } catch (ToolHistoryStorageException exception) {
            return unavailable();
        }
        if (records.isEmpty()) {
            context.output().println("No tool executions recorded.");
            return KaosApplication.SUCCESS;
        }
        context.output().println("Recent tool executions (newest first; content is never stored):");
        for (ToolExecutionRecord record : records) {
            context.output().println(record.completedAt() + " tool=" + record.toolName()
                    + " operation=" + record.operationId()
                    + " decision=" + record.decision().map(Enum::name).orElse("NOT_RECORDED")
                    + " outcome=" + record.outcome());
        }
        return KaosApplication.SUCCESS;
    }

    private int unavailable() {
        ErrorReporter.report(context.errorOutput(), "KAOS-TOOL-HISTORY-001",
                "Tool execution history is unavailable. Check its local data directory and retry.");
        return KaosApplication.APPLICATION_ERROR;
    }
}
