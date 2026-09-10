package io.kaos.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kaos.tool.readlocalfile.ReadLocalFileToolContract;
import io.kaos.tool.websearch.WebSearchToolContract;
import java.util.Objects;
import java.util.Optional;

/** Supplies the exact provider-neutral JSON Schema for one bounded plan proposal. */
public final class AgentPlanFormat {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private AgentPlanFormat() { }

    /**
     * Returns a fresh schema copy whose alternatives cannot omit required current evidence.
     * Java validation remains authoritative after generation.
     */
    public static JsonNode jsonSchema(FreshnessRequirement freshness,
            boolean localEvidenceRequired, Optional<String> explicitPath) {
        Objects.requireNonNull(freshness, "freshness");
        Objects.requireNonNull(explicitPath, "explicitPath");
        ArrayNode alternatives = JSON.arrayNode();
        if (freshness != FreshnessRequirement.REQUIRED && !localEvidenceRequired) {
            alternatives.add(plan(AgentPlan.InformationNeed.STABLE_INTERNAL,
                    JSON.arrayNode().add(synthesis(1))));
        }
        if (freshness != FreshnessRequirement.REQUIRED) {
            alternatives.add(plan(AgentPlan.InformationNeed.LOCAL_EVIDENCE,
                    JSON.arrayNode().add(tool(1, ReadLocalFileToolContract.NAME,
                            localArguments(explicitPath)))
                            .add(synthesis(2))));
        }
        if (!localEvidenceRequired) {
            alternatives.add(plan(AgentPlan.InformationNeed.CURRENT_PUBLIC_EVIDENCE,
                    JSON.arrayNode().add(tool(1, WebSearchToolContract.NAME,
                            WebSearchToolContract.definition().path("function")
                                    .path("parameters")))
                            .add(synthesis(2))));
        }
        alternatives.add(plan(AgentPlan.InformationNeed.MIXED_EVIDENCE,
                JSON.arrayNode().add(tool(1, ReadLocalFileToolContract.NAME,
                        localArguments(explicitPath)))
                        .add(tool(2, WebSearchToolContract.NAME,
                                WebSearchToolContract.definition()
                                        .path("function").path("parameters")))
                        .add(synthesis(3))));
        return JSON.objectNode().set("oneOf", alternatives);
    }

    private static JsonNode localArguments(Optional<String> explicitPath) {
        ObjectNode arguments = ReadLocalFileToolContract.definition()
                .path("function").path("parameters").deepCopy();
        explicitPath.ifPresent(path -> ((ObjectNode) arguments.path("properties").path("path"))
                .put("const", path));
        return arguments;
    }

    private static ObjectNode plan(AgentPlan.InformationNeed need, ArrayNode steps) {
        ObjectNode properties = JSON.objectNode()
                .set("informationNeed", JSON.objectNode().put("const", need.name()));
        properties.set("steps", JSON.objectNode().put("type", "array")
                .put("minItems", steps.size()).put("maxItems", steps.size())
                .set("prefixItems", steps));
        ObjectNode plan = JSON.objectNode().put("type", "object")
                .put("additionalProperties", false);
        plan.set("required", JSON.arrayNode().add("informationNeed").add("steps"));
        plan.set("properties", properties);
        return plan;
    }

    private static ObjectNode synthesis(int sequence) {
        ObjectNode properties = JSON.objectNode()
                .set("sequence", JSON.objectNode().put("const", sequence));
        properties.set("type", JSON.objectNode().put("const", "synthesis"));
        ObjectNode synthesis = JSON.objectNode().put("type", "object")
                .put("additionalProperties", false);
        synthesis.set("required", JSON.arrayNode().add("sequence").add("type"));
        synthesis.set("properties", properties);
        return synthesis;
    }

    private static ObjectNode tool(int sequence, String name, JsonNode arguments) {
        ObjectNode properties = JSON.objectNode()
                .set("sequence", JSON.objectNode().put("const", sequence));
        properties.set("type", JSON.objectNode().put("const", "tool"));
        properties.set("tool", JSON.objectNode().put("const", name));
        properties.set("arguments", arguments.deepCopy());
        ObjectNode tool = JSON.objectNode().put("type", "object")
                .put("additionalProperties", false);
        tool.set("required", JSON.arrayNode().add("sequence").add("type")
                .add("tool").add("arguments"));
        tool.set("properties", properties);
        return tool;
    }
}
