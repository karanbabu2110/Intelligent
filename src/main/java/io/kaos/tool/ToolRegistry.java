package io.kaos.tool;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Read-only, insertion-ordered lookup. Membership conveys no operation authority. */
public final class ToolRegistry {
    private final Map<String, KaosTool<?>> tools;

    public ToolRegistry(Collection<? extends KaosTool<?>> tools) {
        Map<String, KaosTool<?>> entries = new LinkedHashMap<>();
        for (KaosTool<?> tool : tools) {
            if (entries.putIfAbsent(tool.descriptor().name(), tool) != null) {
                throw new IllegalArgumentException("Duplicate tool name: " + tool.descriptor().name());
            }
        }
        this.tools = Collections.unmodifiableMap(entries);
    }

    public Optional<KaosTool<?>> find(String name) { return Optional.ofNullable(tools.get(name)); }

    public KaosTool<?> require(String name) {
        return find(name).orElseThrow(() ->
                new ToolSelectionException(ToolSelectionException.Reason.UNKNOWN_TOOL));
    }

    public List<KaosTool<?>> tools() { return List.copyOf(tools.values()); }
}
