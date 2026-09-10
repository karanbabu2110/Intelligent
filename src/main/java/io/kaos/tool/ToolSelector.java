package io.kaos.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Validates a model's single proposed function call against the exact advertised set. */
public final class ToolSelector {
    private final ToolRegistry registry;
    private final List<String> allowed;

    public ToolSelector(ToolRegistry registry, List<String> allowed) {
        this.registry = Objects.requireNonNull(registry);
        this.allowed = List.copyOf(allowed);
        if (this.allowed.stream().distinct().count() != this.allowed.size()) {
            throw new IllegalArgumentException("Duplicate advertised tool.");
        }
        this.allowed.forEach(registry::require);
    }

    public List<JsonNode> definitions() {
        return allowed.stream().<JsonNode>map(name -> registry.require(name).definition().deepCopy()).toList();
    }

    /** Empty means a direct-answer candidate; the provider still validates its text and stream. */
    public Optional<ToolSelection> select(JsonNode calls) {
        if (calls == null) return Optional.empty();
        if (!calls.isArray() || calls.size() > 1) throw invalid();
        if (calls.isEmpty()) return Optional.empty();
        JsonNode call = calls.get(0);
        JsonNode function = call.get("function");
        if (!call.isObject() || function == null || !function.isObject()
                || !function.path("name").isTextual()) throw invalid();
        String name = function.get("name").textValue();
        KaosTool<?> tool = registry.require(name);
        if (!allowed.contains(name)) {
            throw new ToolSelectionException(ToolSelectionException.Reason.DISALLOWED_TOOL);
        }
        return Optional.of(ToolSelection.decode(tool, function.get("arguments")));
    }

    private static ToolSelectionException invalid() {
        return new ToolSelectionException(ToolSelectionException.Reason.INVALID_RESPONSE);
    }
}
