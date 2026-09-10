package io.kaos.tool.history;

import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.List;

/** Local terminal tool-history boundary. */
public interface ToolExecutionHistory {
    int MAX_READ_RECORDS = 100;

    void record(ToolExecutionRecord record);

    default void record(ToolPermissionPolicy.Snapshot snapshot) {
        record(ToolExecutionRecord.from(snapshot));
    }

    List<ToolExecutionRecord> recent(int limit);
}
