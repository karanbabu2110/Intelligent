package io.kaos.agent;

import io.kaos.tool.readlocalfile.ReadLocalFileToolContract;
import io.kaos.tool.websearch.WebSearchToolContract;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One immutable validated plan for one goal. */
public record AgentPlan(UUID id, AgentGoal goal, InformationNeed informationNeed,
        List<AgentStep> steps) {
    public static final int MAX_STEPS = 3;
    public static final int MAX_TOOL_STEPS = 2;

    public AgentPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(informationNeed, "informationNeed");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        validateBounds(steps);
        validateSequence(steps);
        validateShape(informationNeed, steps);
    }

    public enum InformationNeed {
        STABLE_INTERNAL,
        LOCAL_EVIDENCE,
        CURRENT_PUBLIC_EVIDENCE,
        MIXED_EVIDENCE
    }

    private static void validateBounds(List<AgentStep> steps) {
        if (steps.isEmpty() || steps.size() > MAX_STEPS) {
            throw AgentPlanningException.invalidStructure();
        }
        long toolSteps = steps.stream().filter(AgentStep.Tool.class::isInstance).count();
        if (toolSteps > MAX_TOOL_STEPS) {
            throw new AgentPlanningException(AgentPlanningException.Reason.TOO_MANY_TOOL_STEPS);
        }
    }

    private static void validateSequence(List<AgentStep> steps) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).sequence() != index + 1) {
                throw new AgentPlanningException(AgentPlanningException.Reason.INVALID_SEQUENCE);
            }
        }
    }

    private static void validateShape(InformationNeed need, List<AgentStep> steps) {
        List<String> actual = steps.stream().map(AgentPlan::action).toList();
        List<String> expected = switch (need) {
            case STABLE_INTERNAL -> List.of("synthesis");
            case LOCAL_EVIDENCE -> List.of(ReadLocalFileToolContract.NAME, "synthesis");
            case CURRENT_PUBLIC_EVIDENCE -> List.of(WebSearchToolContract.NAME, "synthesis");
            case MIXED_EVIDENCE -> List.of(ReadLocalFileToolContract.NAME,
                    WebSearchToolContract.NAME, "synthesis");
        };
        if (!actual.equals(expected)) {
            throw AgentPlanningException.invalidStructure();
        }
    }

    private static String action(AgentStep step) {
        return step instanceof AgentStep.Tool tool ? tool.selection().name() : "synthesis";
    }
}
