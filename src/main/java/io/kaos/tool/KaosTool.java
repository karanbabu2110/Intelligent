package io.kaos.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.permission.ToolPermissionPolicy;

/** A statically composed tool with its own request type and resource policy. */
public interface KaosTool<R> {
    ToolDescriptor descriptor();
    JsonNode definition();
    R decodeArguments(JsonNode arguments);
    /** Local configuration syntax only; must not validate targets or contact resources. */
    boolean configured();
    /** Validate the concrete resource scope and prepare an exact approval, without execution. */
    ToolPermissionPolicy<? extends ToolResult<R>> prepare(R request);
}
