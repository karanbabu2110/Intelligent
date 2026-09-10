package io.kaos.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Fourth test-only capability proves that provider and catalog do not enumerate concrete types. */
public final class FixtureTool implements KaosTool<String> {
    public static final String NAME = "fixture_tool";
    private final AtomicInteger preparations = new AtomicInteger();
    private final AtomicInteger executions = new AtomicInteger();
    @Override public ToolDescriptor descriptor() { return new ToolDescriptor(NAME, "Fixture purpose", true); }
    @Override public JsonNode definition() {
        var json = JsonNodeFactory.instance;
        return json.objectNode().put("type", "function").set("function", json.objectNode()
                .put("name", NAME).put("description", "Different model wording")
                .set("parameters", json.objectNode().put("type", "object")
                        .set("properties", json.objectNode().set("value", json.objectNode().put("type", "string")))));
    }
    @Override public String decodeArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject() || arguments.size() != 1
                || !arguments.path("value").isTextual()) {
            throw new ToolSelectionException(ToolSelectionException.Reason.MALFORMED_ARGUMENTS);
        }
        return arguments.get("value").textValue();
    }
    @Override public boolean configured() { return true; }
    @Override public ToolPermissionPolicy<FixtureResult> prepare(String request) {
        preparations.incrementAndGet();
        return new ToolPermissionPolicy<>(NAME, "Approve fixture " + request, response -> {
            var decision = ToolPermissionDecision.parse(response);
            return new ToolPermissionPolicy.Authorization<>(decision,
                    decision == ToolPermissionDecision.APPROVED ? Optional.of(() -> {
                        executions.incrementAndGet();
                        return new FixtureResult(request);
                    }) : Optional.empty());
        }, exception -> false);
    }
    public int preparations() { return preparations.get(); }
    public int executions() { return executions.get(); }
    public record FixtureResult(String request) implements ToolResult<String> {
        @Override public String toolName() { return NAME; }
        @Override public JsonNode modelContent() { return JsonNodeFactory.instance.objectNode().put("echo", request); }
        @Override public String toString() { return "FixtureResult[REDACTED]"; }
    }
}
