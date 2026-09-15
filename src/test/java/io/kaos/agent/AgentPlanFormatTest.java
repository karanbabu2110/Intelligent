package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.websearch.WebSearchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentPlanFormatTest {
    @Test
    void stableSchemaOffersInternalOrPublicEvidenceWithoutInventingLocalEvidence() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.NOT_REQUIRED, false, false,
                Optional.empty()).path("oneOf");

        assertEquals(2, alternatives.size());
        assertEquals(List.of("STABLE_INTERNAL", "CURRENT_PUBLIC_EVIDENCE"),
                informationNeeds(alternatives));
        assertEquals(List.of(1, 2), stepCounts(alternatives));
        assertTool(alternatives.get(1), 0, "web_search", "query",
                WebSearchRequest.MAX_QUERY_CODE_POINTS);
    }

    @Test
    void requiredFreshnessSchemaCannotGenerateAPlanWithoutSearch() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.REQUIRED, false, true,
                Optional.empty());

        assertEquals("CURRENT_PUBLIC_EVIDENCE", informationNeed(alternatives));
        assertTool(alternatives, 0, "web_search", "query",
                WebSearchRequest.MAX_QUERY_CODE_POINTS);
    }

    @Test
    void localOnlyRequirementAllowsOnlyTheLocalShape() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.NOT_REQUIRED, true, false,
                Optional.of("README.md"));

        assertEquals("LOCAL_EVIDENCE", informationNeed(alternatives));
        assertTool(alternatives, 0, "read_local_file", "path",
                ReadLocalFileRequest.MAX_PATH_CODE_POINTS);
    }

    @Test
    void combinedLocalAndCurrentRequirementAllowsOnlyTheMixedShape() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.REQUIRED, true, true,
                Optional.of("README.md"));

        assertEquals("MIXED_EVIDENCE", informationNeed(alternatives));
        assertEquals(3, alternatives.path("properties").path("steps")
                .path("prefixItems").size());
        assertEquals("README.md", alternatives.path("properties").path("steps")
                .path("prefixItems").get(0).path("properties").path("arguments")
                .path("properties").path("path").path("const").asText());
    }

    @Test
    void eachCallReturnsAnIndependentSchemaTree() {
        JsonNode first = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.RECOMMENDED, false, true, Optional.empty());
        ((com.fasterxml.jackson.databind.node.ObjectNode) first).remove("properties");

        assertFalse(AgentPlanFormat.jsonSchema(
                FreshnessRequirement.RECOMMENDED, false, true, Optional.empty())
                .path("properties").isMissingNode());
    }

    private static List<String> informationNeeds(JsonNode alternatives) {
        List<String> values = new ArrayList<>();
        alternatives.forEach(plan -> values.add(plan.path("properties")
                .path("informationNeed").path("const").asText()));
        return values;
    }

    private static String informationNeed(JsonNode plan) {
        return plan.path("properties").path("informationNeed").path("const").asText();
    }

    private static List<Integer> stepCounts(JsonNode alternatives) {
        List<Integer> values = new ArrayList<>();
        alternatives.forEach(plan -> values.add(plan.path("properties").path("steps")
                .path("prefixItems").size()));
        return values;
    }

    private static void assertTool(JsonNode plan, int step, String tool, String argument,
            int maximumLength) {
        JsonNode properties = plan.path("properties").path("steps").path("prefixItems")
                .get(step).path("properties");
        assertEquals(tool, properties.path("tool").path("const").asText());
        JsonNode arguments = properties.path("arguments");
        assertFalse(arguments.path("additionalProperties").asBoolean());
        assertEquals(argument, arguments.path("required").get(0).asText());
        assertEquals(maximumLength,
                arguments.path("properties").path(argument).path("maxLength").asInt());
    }
}
