package io.kaos.tool;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kaos.tool.httpget.HttpGet;
import io.kaos.tool.httpget.HttpGetApproval;
import io.kaos.tool.httpget.HttpGetException;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import io.kaos.tool.readlocalfile.ReadLocalFile;
import io.kaos.tool.readlocalfile.ReadLocalFileApprovalRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileExecutionException;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.websearch.SearxngClient;
import io.kaos.tool.websearch.WebSearch;
import io.kaos.tool.websearch.WebSearchApproval;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolRuntimeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path root;

    @Test void registryPreservesOrderAndDefensivelyCopiesMembership() {
        var tools = new ArrayList<>(registry().tools());
        var registry = new ToolRegistry(tools);
        tools.clear();
        assertEquals(List.of("read_local_file", "http_get", "web_search"),
                registry.tools().stream().map(tool -> tool.descriptor().name()).toList());
        assertSame(registry.tools().getFirst(), registry.require("read_local_file"));
        assertTrue(registry.find("unknown").isEmpty());
        assertReason(ToolSelectionException.Reason.UNKNOWN_TOOL, () -> registry.require("unknown"));
        assertThrows(UnsupportedOperationException.class, () -> registry.tools().clear());
    }

    @Test void rejectsDuplicateNamesDeterministically() {
        var tool = registry().require("read_local_file");
        var exception = assertThrows(IllegalArgumentException.class,
                () -> new ToolRegistry(List.of(tool, tool)));
        assertEquals("Duplicate tool name: read_local_file", exception.getMessage());
    }

    @Test void selectionAndAdvertisementNeverLoadConfiguration() throws Exception {
        var registry = StandardTools.create(() -> { throw new AssertionError(); },
                () -> { throw new AssertionError(); }, () -> { throw new AssertionError(); });
        var selector = new ToolSelector(registry, StandardTools.LOCAL);
        assertEquals(List.of("read_local_file", "web_search"), selector.definitions().stream()
                .map(node -> node.path("function").path("name").asText()).toList());
        assertTrue(selector.select(null).isEmpty());
        assertTrue(selector.select(JSON.createArrayNode()).isEmpty());
        assertEquals("web_search", selector.select(call("web_search", "{\"query\":\"public facts\"}"))
                .orElseThrow().name());
        assertThrows(UnsupportedOperationException.class, () -> selector.definitions().clear());
        ((ObjectNode) selector.definitions().getFirst().get("function")).put("name", "changed");
        assertEquals("read_local_file", selector.definitions().getFirst().path("function").path("name").asText());
    }

    @Test void distinguishesUnknownDisallowedMalformedAndInvalidResponse() throws Exception {
        var selector = new ToolSelector(registry(), StandardTools.LOCAL);
        assertReason(ToolSelectionException.Reason.UNKNOWN_TOOL,
                () -> selector.select(call("secret_name", "{}")));
        assertReason(ToolSelectionException.Reason.DISALLOWED_TOOL,
                () -> selector.select(call("http_get", "{\"url\":\"https://example.com\"}")));
        for (String name : StandardTools.LOCAL) {
            assertReason(ToolSelectionException.Reason.MALFORMED_ARGUMENTS,
                    () -> selector.select(call(name, "{}")));
            assertReason(ToolSelectionException.Reason.MALFORMED_ARGUMENTS,
                    () -> selector.select(call(name, "null")));
        }
        assertReason(ToolSelectionException.Reason.INVALID_RESPONSE,
                () -> selector.select(JSON.createArrayNode().addNull().addNull()));
        assertReason(ToolSelectionException.Reason.INVALID_RESPONSE,
                () -> selector.select(JSON.createArrayNode().addNull()));
        assertReason(ToolSelectionException.Reason.INVALID_RESPONSE,
                () -> selector.select(JSON.createObjectNode()));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolSelector(registry(), List.of("web_search", "web_search")));
    }

    @Test void selectionCopiesArgumentsAndDoesNotAuthorizeExecution() throws Exception {
        Files.writeString(root.resolve("private.txt"), "private content");
        var calls = call("read_local_file", "{\"path\":\"private.txt\"}");
        var selection = new ToolSelector(registry(), StandardTools.READ_LOCAL_FILE).select(calls).orElseThrow();
        ((ObjectNode) calls.get(0).path("function").get("arguments")).put("path", "changed.txt");
        ((ObjectNode) selection.arguments()).put("path", "changed-again.txt");
        assertEquals("private.txt", selection.arguments().path("path").asText());
        assertFalse(selection.toString().contains("private"));
        var permission = selection.prepare();
        assertThrows(IllegalStateException.class, permission::execute);
        assertEquals(ToolPermissionDecision.APPROVED, permission.decide("approve"));
        var result = permission.execute();
        assertTrue(selection.matches(result));
        assertEquals("private content", result.modelContent().path("content").asText());
        ((ObjectNode) result.modelContent()).put("content", "changed");
        assertEquals("private content", result.modelContent().path("content").asText());
        assertEquals(ToolExecutionOutcome.SUCCEEDED, permission.snapshot().outcome());
        assertFalse(permission.snapshot().toString().contains("private"));
        assertFalse(permission.snapshot().updatedAt().isBefore(permission.snapshot().startedAt()));
        assertThrows(IllegalStateException.class, permission::execute);
        assertThrows(IllegalStateException.class, () -> permission.decide("approve"));
    }

    @Test void allConcreteAdaptersFailClosedForDenialInvalidInputAndEof() throws Exception {
        Files.writeString(root.resolve("file.txt"), "text");
        for (String response : new String[] {"deny", "yes", "APPROVE", null}) {
            for (var permission : permissions()) {
                var expected = ToolPermissionDecision.parse(response);
                assertEquals(expected, permission.decide(response));
                assertEquals(expected, permission.snapshot().decision().orElseThrow());
                assertThrows(IllegalStateException.class, permission::execute);
                assertThrows(IllegalStateException.class, () -> permission.decide("approve"));
                assertThrows(IllegalStateException.class, permission::cancel);
            }
        }
    }

    @Test void allConcreteAdaptersCancelBeforeApprovalEvenWithApproveWaiting() throws Exception {
        Files.writeString(root.resolve("file.txt"), "text");
        var permissions = permissions();
        Thread.currentThread().interrupt();
        try {
            for (var permission : permissions) {
                assertEquals(ToolPermissionDecision.CANCELLED, permission.decide("approve"));
                assertEquals(ToolExecutionOutcome.CANCELLED, permission.snapshot().outcome());
                assertThrows(IllegalStateException.class, permission::execute);
            }
        } finally { Thread.interrupted(); }
    }

    @Test void concreteApprovalObjectsAlsoRejectAnArrivingInterrupt() throws Exception {
        Files.writeString(root.resolve("file.txt"), "text");
        var file = new ReadLocalFileApprovalRequest(new ReadLocalFilePermissionValidator(root)
                .validate(new ReadLocalFileRequest("file.txt")));
        var http = new HttpGetApproval(new HttpGetPermissionValidator(Set.of("example.com"))
                .validate(new HttpGetRequest("https://example.com")));
        var search = new WebSearchApproval(new WebSearchRequest("facts"));
        Thread.currentThread().interrupt();
        try {
            assertTrue(file.decide("approve").grant().isEmpty());
            assertTrue(http.decide("approve").grant().isEmpty());
            assertTrue(search.decide("approve").grant().isEmpty());
        } finally { Thread.interrupted(); }
    }

    @Test void failedApprovedAttemptConsumesAuthorityAndPreservesFailure() {
        AtomicInteger calls = new AtomicInteger();
        var failure = new HttpGetException(HttpGetException.Reason.UNAVAILABLE);
        var tool = new HttpGet(() -> new HttpGetPermissionValidator(Set.of("example.com")),
                (validator, grant) -> {
                    grant.claim();
                    calls.incrementAndGet();
                    throw failure;
                });
        var permission = tool.prepare(new HttpGetRequest("https://example.com"));
        permission.decide("approve");
        assertSame(failure, assertThrows(HttpGetException.class, permission::execute));
        assertEquals(ToolExecutionOutcome.FAILED, permission.snapshot().outcome());
        assertEquals(ToolPermissionDecision.APPROVED, permission.snapshot().decision().orElseThrow());
        assertThrows(IllegalStateException.class, permission::execute);
        assertEquals(1, calls.get());
    }

    @Test void cancellationAfterApprovalStillConsumesTheAttempt() throws Exception {
        Files.writeString(root.resolve("file.txt"), "text");
        var permission = new ReadLocalFile(() -> new ReadLocalFilePermissionValidator(root))
                .prepare(new ReadLocalFileRequest("file.txt"));
        permission.decide("approve");
        Thread.currentThread().interrupt();
        try {
            assertThrows(ReadLocalFileExecutionException.class, permission::execute);
            assertEquals(ToolExecutionOutcome.CANCELLED, permission.snapshot().outcome());
            assertThrows(IllegalStateException.class, permission::execute);
        } finally { Thread.interrupted(); }
    }

    @Test void httpAdapterReturnsTypedResultAfterOneConcreteGrantClaim() {
        var tool = new HttpGet(() -> new HttpGetPermissionValidator(Set.of("example.com")),
                (validator, grant) -> {
                    var target = grant.claim();
                    assertThrows(HttpGetException.class, grant::claim);
                    return new HttpGetResult(target.request(), "response", "text/plain");
                });
        var permission = tool.prepare(new HttpGetRequest("https://example.com"));
        permission.decide(" approve ");
        assertEquals("response", permission.execute().content());
        assertEquals(ToolExecutionOutcome.SUCCEEDED, permission.snapshot().outcome());
    }

    @Test void unexpectedExecutorErrorsAreObservedAndRethrown() {
        var failure = new AssertionError("programming error");
        var permission = new ToolPermissionPolicy<String>("test", "private prompt", response ->
                new ToolPermissionPolicy.Authorization<>(ToolPermissionDecision.APPROVED,
                        Optional.of(() -> { throw failure; })), exception -> false);
        permission.decide("approve");
        assertSame(failure, assertThrows(AssertionError.class, permission::execute));
        assertEquals(ToolExecutionOutcome.FAILED, permission.snapshot().outcome());
        assertFalse(permission.toString().contains("private"));
    }

    @Test void malformedAdapterCannotAuthorizeARejectedResponse() {
        var attempts = new AtomicInteger();
        var permission = new ToolPermissionPolicy<String>("test", "prompt", response ->
                new ToolPermissionPolicy.Authorization<>(ToolPermissionDecision.APPROVED,
                        Optional.of(() -> { attempts.incrementAndGet(); return "bad"; })), exception -> false);
        assertThrows(IllegalStateException.class, () -> permission.decide("deny"));
        assertEquals(ToolExecutionOutcome.FAILED, permission.snapshot().outcome());
        assertThrows(IllegalStateException.class, permission::execute);
        assertThrows(IllegalStateException.class, () -> permission.decide("approve"));
        assertEquals(0, attempts.get());
    }

    @Test void concurrentAttemptIsRejectedWhileExecutingSnapshotRemainsObservable() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var tool = new HttpGet(() -> new HttpGetPermissionValidator(Set.of("example.com")),
                (validator, grant) -> {
                    var target = grant.claim();
                    entered.countDown();
                    try {
                        assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new HttpGetException(HttpGetException.Reason.INTERRUPTED);
                    }
                    return new HttpGetResult(target.request(), "text", "text/plain");
                });
        var permission = tool.prepare(new HttpGetRequest("https://example.com"));
        assertTrue(permission.snapshot().decision().isEmpty());
        permission.decide("approve");
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var attempt = executor.submit(permission::execute);
            try {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(ToolExecutionOutcome.EXECUTING, permission.snapshot().outcome());
                assertThrows(IllegalStateException.class, permission::execute);
            } finally { release.countDown(); }
            assertEquals("text", attempt.get(5, java.util.concurrent.TimeUnit.SECONDS).content());
        }
        assertEquals(ToolExecutionOutcome.SUCCEEDED, permission.snapshot().outcome());
    }

    private List<ToolPermissionPolicy<?>> permissions() {
        return List.of(new ReadLocalFile(() -> new ReadLocalFilePermissionValidator(root))
                        .prepare(new ReadLocalFileRequest("file.txt")),
                new HttpGet(() -> new HttpGetPermissionValidator(Set.of("example.com")))
                        .prepare(new HttpGetRequest("https://example.com")),
                new WebSearch(() -> new SearxngClient("http://127.0.0.1:1"))
                        .prepare(new WebSearchRequest("facts")));
    }
    private ToolRegistry registry() {
        return StandardTools.create(() -> new ReadLocalFilePermissionValidator(root),
                () -> new HttpGetPermissionValidator(Set.of("example.com")),
                () -> new SearxngClient("http://127.0.0.1:1"));
    }
    private static JsonNode call(String name, String arguments) throws Exception {
        return JSON.createArrayNode().add(JSON.createObjectNode().set("function",
                JSON.createObjectNode().put("name", name).set("arguments", JSON.readTree(arguments))));
    }
    private static void assertReason(ToolSelectionException.Reason reason,
            org.junit.jupiter.api.function.Executable action) {
        var exception = assertThrows(ToolSelectionException.class, action);
        assertEquals(reason, exception.reason());
        assertFalse(exception.toString().contains("secret_name"));
    }
}
