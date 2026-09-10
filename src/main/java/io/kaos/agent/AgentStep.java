package io.kaos.agent;

import io.kaos.tool.ToolSelection;
import java.util.Objects;

/** One validated sequential step; neither variant grants execution authority. */
public sealed interface AgentStep permits AgentStep.Tool, AgentStep.Synthesis {
    int sequence();

    record Tool(int sequence, ToolSelection selection) implements AgentStep {
        public Tool {
            if (sequence < 1) {
                throw new IllegalArgumentException("tool step sequence must be positive");
            }
            Objects.requireNonNull(selection, "selection");
        }
    }

    record Synthesis(int sequence) implements AgentStep {
        public Synthesis {
            if (sequence < 1) {
                throw new IllegalArgumentException("synthesis step sequence must be positive");
            }
        }
    }
}
