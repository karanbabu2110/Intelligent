package io.kaos.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;

/** KAOS-owned semantic contract, independent of SearXNG response metadata. */
public final class WebSearchToolContract {
    public static final String NAME = "web_search";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private WebSearchToolContract() { }
    public static ObjectNode definition() {
        ObjectNode parameters = JSON.objectNode().put("type", "object");
        parameters.set("properties", JSON.objectNode().set("query",
                JSON.objectNode().put("type", "string").put("minLength", 1)
                        .put("maxLength", WebSearchRequest.MAX_QUERY_CODE_POINTS)));
        parameters.set("required", JSON.arrayNode().add("query"));
        parameters.put("additionalProperties", false);
        return JSON.objectNode().put("type", "function").set("function",
                JSON.objectNode().put("name", NAME)
                        .put("description", "Search public web information after exact user approval. "
                                + "Return titles, URLs and snippets; do not retrieve pages.")
                        .set("parameters", parameters));
    }
    public static WebSearchRequest decodeArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject() || arguments.size() != 1
                || !arguments.path("query").isTextual()) {
            throw new WebSearchException(WebSearchException.Reason.INVALID_REQUEST);
        }
        return new WebSearchRequest(arguments.get("query").textValue());
    }
    public static ObjectNode encodeResult(WebSearchResult result) {
        ObjectNode encoded = JSON.objectNode().put("query", result.request().query());
        var entries = encoded.putArray("results");
        for (var entry : result.results()) {
            entries.addObject().put("title", entry.title()).put("url", entry.url())
                    .put("snippet", entry.snippet());
        }
        if (encoded.toString().getBytes(StandardCharsets.UTF_8).length
                > WebSearchResult.MAX_PAYLOAD_BYTES) {
            throw new WebSearchException(WebSearchException.Reason.RESULT_TOO_LARGE);
        }
        return encoded;
    }
}
