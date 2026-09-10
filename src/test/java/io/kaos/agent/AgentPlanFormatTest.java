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
    void ordinarySchemaOffersOnlyTheFourExactBoundedPlanShapes() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.NOT_REQUIRED, false, Optional.empty()).path("oneOf");

        assertEquals(4, alternatives.size());
        assertEquals(List.of("STABLE_INTERNAL", "LOCAL_EVIDENCE",
                        "CURRENT_PUBLIC_EVIDENCE", "MIXED_EVIDENCE"),
                informationNeeds(alternatives));
        assertEquals(List.of(1, 2, 2, 3), stepCounts(alternatives));
        assertTool(alternatives.get(1), 0, "read_local_file", "path",
                ReadLocalFileRequest.MAX_PATH_CODE_POINTS);
        assertTool(alternatives.get(2), 0, "web_search", "query",
                WebSearchRequest.MAX_QUERY_CODE_POINTS);
        assertTool(alternatives.get(3), 0, "read_local_file", "path",
                ReadLocalFileRequest.MAX_PATH_CODE_POINTS);
        assertTool(alternatives.get(3), 1, "web_search", "query",
                WebSearchRequest.MAX_QUERY_CODE_POINTS);
    }

    @Test
    void requiredFreshnessSchemaCannotGenerateAPlanWithoutSearch() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.REQUIRED, false, Optional.empty()).path("oneOf");

        assertEquals(List.of("CURRENT_PUBLIC_EVIDENCE", "MIXED_EVIDENCE"),
                informationNeeds(alternatives));
        alternatives.forEach(plan -> assertEquals("web_search",
                plan.toString().contains("read_local_file")
                        ? plan.path("properties").path("steps").path("prefixItems")
                                .get(1).path("properties").path("tool").path("const").asText()
                        : plan.path("properties").path("steps").path("prefixItems")
                                .get(0).path("properties").path("tool").path("const").asText()));
    }

    @Test
    void combinedLocalAndCurrentRequirementAllowsOnlyTheMixedShape() {
        JsonNode alternatives = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.REQUIRED, true, Optional.of("README.md")).path("oneOf");

        assertEquals(List.of("MIXED_EVIDENCE"), informationNeeds(alternatives));
        assertEquals(List.of(3), stepCounts(alternatives));
        assertEquals("README.md", alternatives.get(0).path("properties").path("steps")
                .path("prefixItems").get(0).path("properties").path("arguments")
                .path("properties").path("path").path("const").asText());
    }

    @Test
    void eachCallReturnsAnIndependentSchemaTree() {
        JsonNode first = AgentPlanFormat.jsonSchema(
                FreshnessRequirement.RECOMMENDED, false, Optional.empty());
        ((com.fasterxml.jackson.databind.node.ObjectNode) first).remove("oneOf");

        assertFalse(AgentPlanFormat.jsonSchema(
                FreshnessRequirement.RECOMMENDED, false, Optional.empty())
                .path("oneOf").isEmpty());
    }

    private static List<String> informationNeeds(JsonNode alternatives) {
        List<String> values = new ArrayList<>();
        alternatives.forEach(plan -> values.add(plan.path("properties")
                .path("informationNeed").path("const").asText()));
        return values;
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
