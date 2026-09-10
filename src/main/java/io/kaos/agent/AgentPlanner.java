package io.kaos.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.ToolSelection;
import io.kaos.tool.ToolSelectionException;
import io.kaos.tool.ToolSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Decodes one model proposal and validates it without executing or preparing a tool. */
public final class AgentPlanner {
    public static final int MAX_PROPOSAL_CODE_POINTS = 8192;
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                    .maxStringLength(MAX_PROPOSAL_CODE_POINTS).maxNameLength(64).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final ToolSelector selector;
    private final FreshnessPolicy freshnessPolicy = new FreshnessPolicy();
    private final LocalEvidencePolicy localEvidencePolicy = new LocalEvidencePolicy();

    public AgentPlanner(ToolRegistry registry) {
        selector = new ToolSelector(Objects.requireNonNull(registry), StandardTools.LOCAL);
    }

    public AgentPlan plan(AgentGoal goal, String proposal) {
        Objects.requireNonNull(goal, "goal");
        JsonNode root = decode(proposal);
        if (!root.isObject() || root.size() != 2
                || !root.path("informationNeed").isTextual()
                || !root.path("steps").isArray()) {
            throw malformed();
        }
        AgentPlan.InformationNeed need = decodeNeed(root.get("informationNeed").textValue());
        JsonNode proposedSteps = root.get("steps");
        if (proposedSteps.size() > AgentPlan.MAX_STEPS) {
            throw new AgentPlanningException(AgentPlanningException.Reason.TOO_MANY_STEPS);
        }
        long proposedToolSteps = countToolSteps(proposedSteps);
        if (proposedToolSteps > AgentPlan.MAX_TOOL_STEPS) {
            throw new AgentPlanningException(AgentPlanningException.Reason.TOO_MANY_TOOL_STEPS);
        }
        List<AgentStep> steps = new ArrayList<>();
        proposedSteps.forEach(step -> steps.add(decodeStep(step)));
        FreshnessRequirement freshness = freshnessPolicy.assess(goal);
        AgentPlan plan = new AgentPlan(UUID.randomUUID(), goal, need, freshness, steps);
        freshnessPolicy.validate(plan);
        localEvidencePolicy.validate(plan);
        return plan;
    }

    private JsonNode decode(String proposal) {
        if (proposal == null || proposal.isBlank()
                || proposal.codePointCount(0, proposal.length()) > MAX_PROPOSAL_CODE_POINTS) {
            throw malformed();
        }
        try {
            return JSON.readTree(proposal);
        } catch (JsonProcessingException exception) {
            throw malformed();
        }
    }

    private static AgentPlan.InformationNeed decodeNeed(String value) {
        try {
            return AgentPlan.InformationNeed.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw malformed();
        }
    }

    private static long countToolSteps(JsonNode steps) {
        long count = 0;
        for (JsonNode step : steps) {
            if (step.isObject() && step.path("type").isTextual()
                    && "tool".equals(step.get("type").textValue())) {
                count++;
            }
        }
        return count;
    }

    private AgentStep decodeStep(JsonNode step) {
        if (!step.isObject() || !step.path("sequence").isIntegralNumber()
                || !step.path("sequence").canConvertToInt()
                || !step.path("type").isTextual()) {
            throw malformed();
        }
        int sequence = step.get("sequence").intValue();
        return switch (step.get("type").textValue()) {
            case "synthesis" -> decodeSynthesis(step, sequence);
            case "tool" -> decodeTool(step, sequence);
            default -> throw malformed();
        };
    }

    private static AgentStep decodeSynthesis(JsonNode step, int sequence) {
        if (step.size() != 2) {
            throw malformed();
        }
        try {
            return new AgentStep.Synthesis(sequence);
        } catch (IllegalArgumentException exception) {
            throw new AgentPlanningException(AgentPlanningException.Reason.INVALID_SEQUENCE);
        }
    }

    private AgentStep decodeTool(JsonNode step, int sequence) {
        if (step.size() != 4 || !step.path("tool").isTextual()
                || !step.has("arguments")) {
            throw malformed();
        }
        JsonNode calls = JSON.createArrayNode().add(JSON.createObjectNode().set("function",
                JSON.createObjectNode().put("name", step.get("tool").textValue())
                        .set("arguments", step.get("arguments"))));
        try {
            ToolSelection selection = selector.select(calls).orElseThrow();
            return new AgentStep.Tool(sequence, selection);
        } catch (ToolSelectionException exception) {
            throw map(exception);
        } catch (IllegalArgumentException exception) {
            throw new AgentPlanningException(AgentPlanningException.Reason.INVALID_SEQUENCE);
        }
    }

    private static AgentPlanningException map(ToolSelectionException exception) {
        AgentPlanningException.Reason reason = switch (exception.reason()) {
            case UNKNOWN_TOOL -> AgentPlanningException.Reason.UNKNOWN_TOOL;
            case DISALLOWED_TOOL -> AgentPlanningException.Reason.DISALLOWED_TOOL;
            case MALFORMED_ARGUMENTS -> AgentPlanningException.Reason.MALFORMED_ARGUMENTS;
            case INVALID_RESPONSE -> AgentPlanningException.Reason.MALFORMED_RESPONSE;
        };
        return new AgentPlanningException(reason);
    }

    private static AgentPlanningException malformed() {
        return new AgentPlanningException(AgentPlanningException.Reason.MALFORMED_RESPONSE);
    }
}
