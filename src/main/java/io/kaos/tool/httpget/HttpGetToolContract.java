package io.kaos.tool.httpget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Owns the fixed provider-visible contract for the second KAOS tool. */
public final class HttpGetToolContract {
    public static final String NAME = "http_get";
    public static final String URL_ARGUMENT = "url";
    public static final String DESCRIPTION =
            "Retrieve one approved bounded UTF-8 text resource from an allowed HTTPS host.";

    private HttpGetToolContract() {
    }

    public static ObjectNode definition() {
        JsonNodeFactory json = JsonNodeFactory.instance;
        ObjectNode url = json.objectNode()
                .put("type", "string")
                .put("description", "One absolute HTTPS URL on an allowed host.")
                .put("maxLength", HttpGetRequest.MAX_URL_CODE_POINTS);
        ObjectNode properties = json.objectNode().set(URL_ARGUMENT, url);
        ObjectNode parameters = json.objectNode()
                .put("type", "object")
                .set("properties", properties);
        parameters.set("required", json.arrayNode().add(URL_ARGUMENT));
        parameters.put("additionalProperties", false);
        ObjectNode function = json.objectNode()
                .put("name", NAME)
                .put("description", DESCRIPTION)
                .set("parameters", parameters);
        return json.objectNode().put("type", "function").set("function", function);
    }

    public static HttpGetRequest decodeArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject() || arguments.size() != 1
                || !arguments.has(URL_ARGUMENT)
                || !arguments.get(URL_ARGUMENT).isTextual()) {
            throw new IllegalArgumentException(
                    "tool arguments must contain exactly one textual URL");
        }
        return new HttpGetRequest(arguments.get(URL_ARGUMENT).textValue());
    }

    public static ObjectNode encodeResult(HttpGetResult result) {
        Objects.requireNonNull(result, "result");
        return JsonNodeFactory.instance.objectNode()
                .put(URL_ARGUMENT, result.request().url())
                .put("media_type", result.mediaType())
                .put("content", result.content())
                .put("utf8_bytes", result.utf8ByteCount());
    }
}
