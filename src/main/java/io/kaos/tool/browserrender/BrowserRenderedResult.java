package io.kaos.tool.browserrender;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaos.tool.ToolResult;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded visible page text and metadata from the research-only Chromium fallback. */
public record BrowserRenderedResult(HttpGetRequest request, String content, String title, String description)
        implements ToolResult<HttpGetRequest> {
    public static final String TOOL_NAME = "research_browser_render";
    public static final int MAX_TITLE_CODE_POINTS = 256;
    public static final int MAX_DESCRIPTION_CODE_POINTS = 512;

    public BrowserRenderedResult(HttpGetRequest request, String content) {
        this(request, content, "", "");
    }

    public BrowserRenderedResult {
        Objects.requireNonNull(request, "request");
        if (content == null || content.isBlank()
                || content.getBytes(StandardCharsets.UTF_8).length > HttpGetResult.MAX_MODEL_TEXT_UTF8_BYTES
                || content.codePointCount(0, content.length()) > HttpGetResult.MAX_MODEL_TEXT_CODE_POINTS
                || content.codePoints().anyMatch(BrowserRenderedResult::unsafeControl)) {
            throw new IllegalArgumentException("Rendered page text is invalid.");
        }
        checkMetadata(title, MAX_TITLE_CODE_POINTS);
        checkMetadata(description, MAX_DESCRIPTION_CODE_POINTS);
    }

    @Override public String toolName() {
        return TOOL_NAME;
    }

    @Override public JsonNode modelContent() {
        return JsonNodeFactory.instance.objectNode()
                .put("url", request.url())
                .put("evidence_type", "browser_rendered")
                .put("content", content)
                .put("title", title)
                .put("description", description)
                .put("utf8_bytes", content.getBytes(StandardCharsets.UTF_8).length);
    }

    private static void checkMetadata(String value, int maxPoints) {
        if (value == null || value.codePointCount(0, value.length()) > maxPoints
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Rendered page metadata is invalid.");
        }
    }

    private static boolean unsafeControl(int point) {
        return Character.isISOControl(point) && point != '\n' && point != '\r' && point != '\t';
    }
}
