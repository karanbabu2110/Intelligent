package io.kaos.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** Owns the one fixed provider-visible contract for the first KAOS tool. */
public final class ReadLocalFileToolContract {
    public static final String NAME = "read_local_file";
    public static final String PATH_ARGUMENT = "path";
    public static final String DESCRIPTION =
            "Read one approved UTF-8 text-based file below the configured local read root.";

    private ReadLocalFileToolContract() {
    }

    /** Returns the exact Ollama-compatible function definition for this tool. */
    public static ObjectNode definition() {
        JsonNodeFactory json = JsonNodeFactory.instance;
        ObjectNode path = json.objectNode()
                .put("type", "string")
                .put("description", "One normalized relative path below the local read root.")
                .put("maxLength", ReadLocalFileRequest.MAX_PATH_CODE_POINTS);
        ObjectNode properties = json.objectNode().set(PATH_ARGUMENT, path);
        ObjectNode parameters = json.objectNode()
                .put("type", "object")
                .set("properties", properties);
        parameters.set("required", json.arrayNode().add(PATH_ARGUMENT));
        parameters.put("additionalProperties", false);

        ObjectNode function = json.objectNode()
                .put("name", NAME)
                .put("description", DESCRIPTION)
                .set("parameters", parameters);
        return json.objectNode().put("type", "function").set("function", function);
    }

    /** Decodes only the exact one-string-argument shape advertised by the definition. */
    public static ReadLocalFileRequest decodeArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()
                || arguments.size() != 1
                || !arguments.has(PATH_ARGUMENT)
                || !arguments.get(PATH_ARGUMENT).isTextual()) {
            throw new IllegalArgumentException(
                    "tool arguments must contain exactly one textual path");
        }
        return new ReadLocalFileRequest(arguments.get(PATH_ARGUMENT).textValue());
    }

    /** Encodes the complete private tool result intended for the continuing model request. */
    public static ObjectNode encodeResult(ReadLocalFileResult result) {
        Objects.requireNonNull(result, "result");
        return JsonNodeFactory.instance.objectNode()
                .put(PATH_ARGUMENT, result.request().path())
                .put("content", result.content())
                .put("utf8_bytes", result.utf8ByteCount());
    }
}
