package io.kaos.app;

import io.kaos.tool.history.ToolExecutionHistory;
import io.kaos.tool.history.ToolExecutionRecord;
import java.util.ArrayList;
import java.util.List;

final class RecordingToolHistory implements ToolExecutionHistory {
    private final List<ToolExecutionRecord> records = new ArrayList<>();
    @Override public void record(ToolExecutionRecord record) { records.add(record); }
    @Override public List<ToolExecutionRecord> recent(int limit) {
        return List.copyOf(records.reversed().stream().limit(limit).toList());
    }
    List<ToolExecutionRecord> records() { return List.copyOf(records); }
}
