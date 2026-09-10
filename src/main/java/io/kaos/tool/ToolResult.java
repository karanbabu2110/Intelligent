package io.kaos.tool;

import com.fasterxml.jackson.databind.JsonNode;

/** Private bounded result for one model continuation, never an execution-history payload. */
public interface ToolResult<R> {
    String toolName();
    R request();
    JsonNode modelContent();
}
