package io.kaos.tool.browserrender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaos.tool.ToolResult;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded visible page text produced by the research-only Chromium fallback. */
public record BrowserRenderedResult(HttpGetRequest request, String content)
        implements ToolResult<HttpGetRequest> {
    public static final String TOOL_NAME = "research_browser_render";

    public BrowserRenderedResult {
        Objects.requireNonNull(request, "request");
        if (content == null || content.isBlank()
                || content.getBytes(StandardCharsets.UTF_8).length > HttpGetResult.MAX_MODEL_TEXT_UTF8_BYTES
                || content.codePointCount(0, content.length()) > HttpGetResult.MAX_MODEL_TEXT_CODE_POINTS
                || content.codePoints().anyMatch(BrowserRenderedResult::unsafeControl)) {
            throw new IllegalArgumentException("Rendered page text is invalid.");
        }
    }

    @Override public String toolName() {
        return TOOL_NAME;
    }

    @Override public JsonNode modelContent() {
        return JsonNodeFactory.instance.objectNode()
                .put("url", request.url())
                .put("evidence_type", "browser_rendered")
                .put("content", content)
                .put("utf8_bytes", content.getBytes(StandardCharsets.UTF_8).length);
    }

    private static boolean unsafeControl(int point) {
        return Character.isISOControl(point) && point != '\n' && point != '\r' && point != '\t';
    }
}
