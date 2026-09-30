package io.kaos.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.kaos.agent.AgentGoal;
import io.kaos.ai.ollama.OllamaModelConfiguration;
import io.kaos.ai.ollama.OllamaPrompt;
import io.kaos.ai.ollama.OllamaPromptClient;
import io.kaos.diagnostics.DebugTrace;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.ToolResult;
import io.kaos.tool.ToolSelection;
import io.kaos.tool.ToolSelector;
import io.kaos.tool.httpget.HttpGetException;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import io.kaos.tool.websearch.WebSearchException;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Fixed foreground research sequence; no mutable agent plan or model-driven execution loop. */
final class ResearchCommand {
    @FunctionalInterface
    interface Prompts {
        OllamaPromptClient.Result submit(OllamaModelConfiguration model, OllamaPrompt prompt,
                List<ToolResult<?>> evidence, JsonNode format);
    }

    private final CommandContext context;
    private final Supplier<OllamaModelConfiguration> modelLoader;
    private final Supplier<ToolRegistry> registryLoader;
    private final Supplier<ResearchHostApprovals> approvalsLoader;
    private final Prompts prompts;
    private ToolHistoryRecorder history;

    ResearchCommand(CommandContext context, Supplier<OllamaModelConfiguration> modelLoader,
            Supplier<ToolRegistry> registryLoader, Supplier<ResearchHostApprovals> approvalsLoader,
            Prompts prompts) {
        this.context = context;
        this.modelLoader = modelLoader;
        this.registryLoader = registryLoader;
        this.approvalsLoader = approvalsLoader;
        this.prompts = prompts;
    }

    ResearchCommand withToolHistory(Supplier<io.kaos.tool.history.ToolExecutionHistory> loader) {
        history = new ToolHistoryRecorder(context, loader);
        return this;
    }

    int execute(String objective) {
        // Existing opt-in debug tracing can contain payloads. Research never emits those payloads.
        try (var trace = DebugTrace.open(false, context.errorOutput())) {
            return run(objective);
        }
    }

    private int run(String objective) {
        int retrieved = 0;
        int selected = 0;
        String stage = "CONFIGURATION";
        var prepared = new ArrayList<ToolPermissionPolicy<? extends ToolResult<?>>>();
        try {
            var goal = new AgentGoal(UUID.randomUUID(), objective);
            var query = new WebSearchRequest(goal.objective());
            var model = modelLoader.get();
            var registry = registryLoader.get();
            var approvals = approvalsLoader.get();
            var savedHosts = approvals.read();
            var reader = new BufferedReader(new InputStreamReader(context.input(),
                    StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)));
            var searchSelection = select(registry, "web_search", "query", query.query());
            stage = "SEARCH";
            var searchPermission = searchSelection.prepare();
            prepared.add(searchPermission);
            context.output().println(searchPermission.prompt());
            if (searchPermission.decide(ApprovalInput.readBounded(reader)) != ToolPermissionDecision.APPROVED) {
                record(searchPermission);
                return stopped("SEARCH_NOT_APPROVED", 0, 0);
            }
            ToolResult<?> searched = attempt(searchPermission);
            record(searchPermission);
            if (!searchSelection.matches(searched) || !(searched instanceof WebSearchResult search)) {
                throw new IllegalArgumentException();
            }
            if (search.results().isEmpty()) return stopped("NO_SEARCH_RESULTS", 0, 0);
            checkInterrupted();
            stage = "SELECTION";
            context.output().println("Search returned " + search.results().size()
                    + " discovery results. Linked pages have not been read.");
            var proposal = prompts.submit(model, new OllamaPrompt(goal.objective(), ResearchFormat.SELECT),
                    List.of(search), ResearchFormat.selectionSchema());
            requireModel(proposal);
            var candidates = ResearchFormat.candidates(proposal.response(), search.results().size());
            checkInterrupted();
            stage = "SOURCE_VALIDATION";
            var urls = new ArrayList<String>();
            var selections = new ArrayList<ToolSelection>();
            var unique = new HashSet<String>();
            for (var candidate : candidates) {
                String url = HttpGetPermissionValidator.validateSyntax(new HttpGetRequest(
                        search.results().get(candidate.result() - 1).url())).uri().toASCIIString();
                if (!unique.add(url)) throw new IllegalArgumentException();
                urls.add(url);
                selections.add(select(registry, "http_get", "url", url));
            }
            selected = urls.size();
            context.output().println("Proposed sources (model judgments, not verification):");
            for (int i = 0; i < selected; i++) {
                var candidate = candidates.get(i);
                context.output().println("[" + (i + 1) + "] Exact normalized URL: " + urls.get(i));
                context.output().println("Role: " + candidate.role() + "; purpose: " + candidate.purpose());
                context.output().println("Suitability reason: " + candidate.reason());
            }
            context.output().println("URL syntax passed; public DNS and network safety "
                    + "will be checked independently at each connection. This does not establish evidence strength.");
            context.output().println("Research limits: one GET per selected URL, at most 512 KiB raw and 64 KiB extracted text and "
                    + "15 seconds each; at most 98304 bytes and 45 seconds of retrieval in total. "
                    + "URL paths/queries reach these hosts. Responses go only to local Ollama for this answer. "
                    + "No redirects, retries or further links.");
            var newHosts = new java.util.TreeSet<String>();
            for (String url : urls) {
                String host = java.net.URI.create(url).getHost();
                if (!savedHosts.contains(host)) newHosts.add(host);
            }
            if (!newHosts.isEmpty()) {
                context.output().println("New publisher hostnames: " + String.join(", ", newHosts));
                context.output().println("Type 'approve' to remember these exact hosts for current and future "
                        + "research reads of any HTTPS page on them, or 'deny' to cancel. Subdomains are separate. "
                        + "This approves access, not factual trust.");
                context.output().println("Approval file: " + approvals.path()
                        + ". Remove a hostname from this file to revoke it.");
                if (ToolPermissionDecision.parse(ApprovalInput.readBounded(reader)) != ToolPermissionDecision.APPROVED) {
                    return stopped("SOURCES_NOT_APPROVED", 0, selected);
                }
                checkInterrupted();
                approvals.remember(newHosts);
                context.output().println("Publisher host approvals saved.");
            } else {
                context.output().println("Using saved publisher host approvals; no new page approval required.");
            }
            // Each persisted host decision authorizes a fresh, exact single-use request grant.
            var retrievals = selections.stream().map(ToolSelection::prepare).toList();
            prepared.addAll(retrievals);
            var pages = new ArrayList<ToolResult<?>>();
            var successfulCandidates = new ArrayList<ResearchFormat.Candidate>();
            var successfulUrls = new ArrayList<String>();
            var sourceIds = new ArrayList<Integer>();
            stage = "RETRIEVAL";
            for (int i = 0; i < selected; i++) {
                checkInterrupted();
                if (!approvals.read().contains(java.net.URI.create(urls.get(i)).getHost())) {
                    return stopped("HOST_APPROVAL_REVOKED", retrieved, selected);
                }
                var permission = retrievals.get(i);
                if (permission.decide("approve") != ToolPermissionDecision.APPROVED) {
                    record(permission);
                    return stopped("CANCELLED", retrieved, selected);
                }
                ToolResult<?> page;
                try {
                    page = attempt(permission);
                } catch (HttpGetException exception) {
                    if (exception.reason() == HttpGetException.Reason.INTERRUPTED
                            || exception.reason() == HttpGetException.Reason.APPROVAL_REUSED
                            || exception.reason() == HttpGetException.Reason.INVALID_REQUEST
                            || exception.reason() == HttpGetException.Reason.INVALID_CONFIGURATION) throw exception;
                    checkInterrupted();
                    context.errorOutput().println("ERROR: Source [" + (i + 1) + "] retrieval failed: "
                            + exception.status().name() + "; reason=" + exception.reason().name()
                            + (exception.failureDetail().isBlank() ? "" : "; detail=" + exception.failureDetail())
                            + "; httpStatus=" + exception.httpStatus()
                            + "; contentType=" + (exception.contentType().isBlank() ? "unknown" : exception.contentType())
                            + "; finalUrl=" + (exception.finalUrl().isBlank() ? "unknown" : exception.finalUrl())
                            + "; continuing with remaining approved sources.");
                    continue;
                }
                record(permission);
                if (!selections.get(i).matches(page) || !(page instanceof HttpGetResult)) {
                    throw new IllegalArgumentException();
                }
                pages.add(page);
                successfulCandidates.add(candidates.get(i));
                successfulUrls.add(urls.get(i));
                sourceIds.add(i + 1);
                retrieved++;
                context.output().println("Retrieved source [" + (i + 1) + "] successfully.");
            }
            checkInterrupted();
            if (pages.isEmpty()) {
                stage = "SEARCH_ONLY_SYNTHESIS";
                var fallbackEntries = new ArrayList<WebSearchResult.Entry>();
                for (int i = 0; i < selected; i++) {
                    var discovered = search.results().get(candidates.get(i).result() - 1);
                    fallbackEntries.add(new WebSearchResult.Entry(
                            discovered.title(), urls.get(i), discovered.snippet()));
                }
                var fallback = prompts.submit(model,
                        new OllamaPrompt(goal.objective(), ResearchFormat.SYNTHESIZE_SEARCH_ONLY),
                        List.of(new WebSearchResult(search.request(), fallbackEntries)),
                        ResearchFormat.searchOnlyAnswerSchema());
                requireModel(fallback);
                checkInterrupted();
                var answer = ResearchFormat.searchOnlyAnswer(fallback.response(), candidates);
                if (answer.claims().isEmpty()) {
                    context.output().println("Insufficient search evidence: " + answer.uncertainty());
                    return stopped("INSUFFICIENT_SEARCH_EVIDENCE", retrieved, selected);
                }
                context.output().println("Search-only outcome (no selected page was retrieved; titles and snippets "
                        + "are unverified discovery evidence):");
                for (var claim : answer.claims()) {
                    context.output().println(claim.kind() + ": " + claim.text() + " " + claim.sources());
                }
                context.output().println("Uncertainty: " + answer.uncertainty());
                for (int i = 0; i < urls.size(); i++) {
                    context.output().println("[" + (i + 1) + "] SEARCH RESULT ONLY: " + urls.get(i));
                }
                return KaosApplication.SUCCESS;
            }
            context.output().println("Evidence coverage: retrieved " + retrieved + "/" + selected
                    + " approved sources." + (retrieved < selected
                            ? " Partial retrieval; failed sources are excluded from the answer." : ""));
            stage = "SYNTHESIS";
            String roles = " Provisional source roles in order: "
                    + successfulCandidates.stream().map(ResearchFormat.Candidate::role).toList() + "."
                    + (retrieved < selected ? " Partial retrieval: use only supplied evidence; acknowledge missing sources." : "");
            var synthesis = prompts.submit(model,
                    new OllamaPrompt(goal.objective(), ResearchFormat.SYNTHESIZE + roles),
                    List.copyOf(pages), ResearchFormat.answerSchema());
            requireModel(synthesis);
            checkInterrupted();
            var answer = ResearchFormat.answer(synthesis.response(), successfulCandidates);
            // Host separation is a necessary coarse guard, not proof of publisher independence.
            for (var claim : answer.claims()) {
                if (claim.kind().equals("FACT")
                        && claim.sources().stream().noneMatch(id -> successfulCandidates.get(id - 1).role().equals("PRIMARY"))
                        && claim.sources().stream().map(id -> java.net.URI.create(successfulUrls.get(id - 1)).getHost())
                                .distinct().count() < 2) throw new IllegalArgumentException();
            }
            if (answer.claims().isEmpty()) {
                context.output().println("Insufficient retrieved evidence: " + answer.uncertainty());
                return stopped("INSUFFICIENT_EVIDENCE", retrieved, selected);
            }
            context.output().println("Research answer (attribution is checked; factual correctness is not guaranteed):");
            for (var claim : answer.claims()) {
                context.output().println(claim.kind() + ": " + claim.text() + " "
                        + claim.sources().stream().map(id -> sourceIds.get(id - 1)).toList());
            }
            context.output().println("Uncertainty: " + answer.uncertainty());
            for (int i = 0; i < successfulUrls.size(); i++) {
                context.output().println("[" + sourceIds.get(i) + "] " + successfulUrls.get(i));
            }
            return KaosApplication.SUCCESS;
        } catch (ResearchHostApprovals.Unavailable exception) {
            return stopped("HOST_APPROVAL_STORE_UNAVAILABLE", retrieved, selected);
        } catch (ResearchFailure exception) {
            return stopped(exception.code, retrieved, selected);
        } catch (HttpGetException exception) {
            return stopped(exception.reason().name(), retrieved, selected);
        } catch (WebSearchException exception) {
            return stopped(exception.reason().name(), retrieved, selected);
        } catch (java.io.IOException exception) {
            return stopped("INPUT_FAILED", retrieved, selected);
        } catch (RuntimeException exception) {
            return stopped(Thread.currentThread().isInterrupted() ? "CANCELLED" : stage + "_INVALID_OR_UNAVAILABLE",
                    retrieved, selected);
        } finally {
            for (var permission : prepared) {
                if (permission.snapshot().outcome() == io.kaos.tool.ToolExecutionOutcome.APPROVAL_REQUIRED) {
                    permission.cancel();
                    if (history != null) history.record(permission.snapshot());
                }
            }
        }
    }

    private ToolResult<?> attempt(ToolPermissionPolicy<? extends ToolResult<?>> permission) {
        try { return permission.execute(); }
        catch (RuntimeException exception) {
            record(permission);
            throw exception;
        }
    }

    private void record(ToolPermissionPolicy<?> permission) {
        if (history != null && !history.record(permission.snapshot())) throw new IllegalStateException();
    }

    private static ToolSelection select(ToolRegistry registry, String name, String key, String value) {
        var calls = JsonNodeFactory.instance.arrayNode();
        calls.addObject().putObject("function").put("name", name)
                .putObject("arguments").put(key, value);
        return new ToolSelector(registry, List.of(name)).select(calls).orElseThrow();
    }

    private static void requireModel(OllamaPromptClient.Result result) {
        if (!result.successful()) throw new ResearchFailure("MODEL_" + result.status().name());
        if (result.toolRequested()) throw new ResearchFailure("MODEL_REQUESTED_TOOL");
    }

    private static final class ResearchFailure extends RuntimeException {
        private final String code;
        ResearchFailure(String code) { super(code); this.code = code; }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException();
    }

    private int stopped(String reason, int retrieved, int selected) {
        context.output().println("Research stopped: " + reason + "; retrieved " + retrieved + "/" + selected
                + " sources. No final answer was produced. Start a new run with a new search approval.");
        return KaosApplication.APPLICATION_ERROR;
    }
}
