package io.kaos.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Validated identity and arguments, bound to the resolved tool; it grants no execution authority. */
public final class ToolSelection {
    private final String name;
    private final JsonNode arguments;
    private final Object request;
    private final Supplier<ToolPermissionPolicy<? extends ToolResult<?>>> preparation;

    private <R> ToolSelection(KaosTool<R> tool, JsonNode arguments, R request) {
        this.name = tool.descriptor().name();
        this.arguments = arguments.deepCopy();
        this.request = Objects.requireNonNull(request);
        this.preparation = () -> tool.prepare(request);
    }

    static <R> ToolSelection decode(KaosTool<R> tool, JsonNode arguments) {
        return new ToolSelection(tool, arguments, tool.decodeArguments(arguments));
    }

    public String name() { return name; }
    public JsonNode arguments() { return arguments.deepCopy(); }
    /** Compatibility access for existing typed command APIs; no dispatch or discovery uses this. */
    public <R> Optional<R> request(Class<R> type) {
        return type.isInstance(request) ? Optional.of(type.cast(request)) : Optional.empty();
    }
    public ToolPermissionPolicy<? extends ToolResult<?>> prepare() { return preparation.get(); }
    public boolean matches(ToolResult<?> result) {
        return name.equals(result.toolName()) && request.equals(result.request());
    }
    @Override public String toString() { return "ToolSelection[name=" + name + "]"; }
}
