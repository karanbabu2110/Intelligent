package io.kaos.tool.websearch;

import com.fasterxml.jackson.databind.JsonNode;
import io.kaos.tool.KaosTool;
import io.kaos.tool.ToolDescriptor;
import io.kaos.tool.ToolSelectionException;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Objects;
import java.util.function.Supplier;

/** Runtime adapter preserving the configured endpoint and exact-query disclosure/approval. */
public final class WebSearch implements KaosTool<WebSearchRequest> {
    private final Supplier<SearxngClient> configuration;
    public WebSearch(Supplier<SearxngClient> configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }
    @Override public ToolDescriptor descriptor() {
        return new ToolDescriptor(WebSearchToolContract.NAME, "Discover public URLs through SearXNG", true);
    }
    @Override public JsonNode definition() { return WebSearchToolContract.definition(); }
    @Override public WebSearchRequest decodeArguments(JsonNode arguments) {
        try { return WebSearchToolContract.decodeArguments(arguments); }
        catch (WebSearchException exception) {
            if (exception.reason() != WebSearchException.Reason.INVALID_REQUEST) throw exception;
            throw new ToolSelectionException(ToolSelectionException.Reason.MALFORMED_ARGUMENTS);
        }
    }
    @Override public boolean configured() {
        try { return configuration.get() != null; }
        catch (WebSearchException exception) { return false; }
    }
    @Override public ToolPermissionPolicy<WebSearchResult> prepare(WebSearchRequest request) {
        var client = configuration.get();
        var approval = new WebSearchApproval(request);
        return new ToolPermissionPolicy<>(WebSearchToolContract.NAME, approval.prompt(), response -> {
            var outcome = approval.decide(response);
            return new ToolPermissionPolicy.Authorization<>(
                    ToolPermissionDecision.valueOf(outcome.status().name()),
                    outcome.grant().map(grant -> () -> client.execute(grant)));
        }, exception -> exception instanceof WebSearchException failure
                && failure.reason() == WebSearchException.Reason.CANCELLED,
                decision -> "Search not approved. No search request was made.");
    }
}
