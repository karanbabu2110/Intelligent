package io.kaos.tool.readlocalfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

class ReadLocalFileToolContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void advertisesOneExactRequiredStringArgument() throws Exception {
        JsonNode definition = ReadLocalFileToolContract.definition();
        JsonNode expected = JSON.readTree("""
                {
                  "type": "function",
                  "function": {
                    "name": "read_local_file",
                    "description": "Read one approved UTF-8 text-based file below the configured local read root.",
                    "parameters": {
                      "type": "object",
                      "properties": {
                        "path": {
                          "type": "string",
                          "description": "One normalized relative path below the local read root.",
                          "maxLength": 512
                        }
                      },
                      "required": ["path"],
                      "additionalProperties": false
                    }
                  }
                }
                """);

        assertEquals(expected, definition);
    }

    @Test
    void decodesOnlyTheAdvertisedArgumentShape() throws Exception {
        ReadLocalFileRequest request = ReadLocalFileToolContract.decodeArguments(
                JSON.readTree("{\"path\":\"src/Main.java\"}"));

        assertEquals("src/Main.java", request.path());
    }

    @Test
    void rejectsMissingExtraAndNonTextArgumentsWithoutEchoingThem() throws Exception {
        String privatePath = "private/customer.java";
        for (JsonNode arguments : new JsonNode[] {
                null,
                JsonNodeFactory.instance.arrayNode(),
                JSON.readTree("{}"),
                JSON.readTree("{\"path\":23}"),
                JSON.readTree("{\"path\":\"" + privatePath + "\",\"extra\":true}")}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ReadLocalFileToolContract.decodeArguments(arguments));
            assertEquals("tool arguments must contain exactly one textual path",
                    exception.getMessage());
            assertFalse(exception.getMessage().contains(privatePath));
        }
    }

    @Test
    void encodesTheCompleteResultForTheContinuingModelRequest() {
        ReadLocalFileResult result = new ReadLocalFileResult(
                new ReadLocalFileRequest("src/Main.java"), "class Main {}\n");

        JsonNode encoded = ReadLocalFileToolContract.encodeResult(result);

        assertEquals("src/Main.java", encoded.get("path").textValue());
        assertEquals("class Main {}\n", encoded.get("content").textValue());
        assertEquals(14, encoded.get("utf8_bytes").intValue());
        assertEquals(3, encoded.size());
    }

    @Test
    void rejectsANullResult() {
        assertThrows(NullPointerException.class,
                () -> ReadLocalFileToolContract.encodeResult(null));
    }

    @Test
    void constantsRemainAlignedWithTheDefinition() {
        assertEquals("read_local_file", ReadLocalFileToolContract.NAME);
        assertEquals("path", ReadLocalFileToolContract.PATH_ARGUMENT);
        assertTrue(ReadLocalFileToolContract.DESCRIPTION.contains("approved"));
    }
}
