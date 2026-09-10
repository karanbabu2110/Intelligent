package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolExecutionOutcome;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import io.kaos.tool.websearch.SearxngClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentApprovalCheckpointTest {
    @TempDir Path root;

    @Test
    void eachMixedToolStepExposesAndRequiresItsOwnExactApproval() throws Exception {
        Files.writeString(root.resolve("project.md"), "local evidence");
        AgentExecutor executor = new AgentExecutor(mixedPlan());

        String filePrompt = executor.currentApprovalPrompt();
        var filePermission = executor.prepareCurrent();
        assertTrue(filePrompt.contains("Tool: read_local_file"));
        assertTrue(filePrompt.contains(root.resolve("project.md").toString()));
        assertTrue(filePrompt.contains("configured local Ollama model"));
        assertEquals(ToolPermissionDecision.APPROVED, executor.decideCurrent("approve"));
        ReadLocalFileResult fileResult = (ReadLocalFileResult) executor.executeCurrent();

        String searchPrompt = executor.currentApprovalPrompt();
        var searchPermission = executor.prepareCurrent();
        assertTrue(searchPrompt.contains("Tool: web_search"));
        assertTrue(searchPrompt.contains("\"current SearXNG guidance\""));
        assertTrue(searchPrompt.contains("external search engines"));
        assertNotEquals(filePermission.snapshot().operationId(),
                searchPermission.snapshot().operationId());
        assertEquals(ToolExecutionOutcome.APPROVAL_REQUIRED,
                searchPermission.snapshot().outcome());
        assertThrows(IllegalStateException.class, searchPermission::execute);

        assertEquals(ToolPermissionDecision.DENIED, executor.decideCurrent("deny"));
        assertEquals(AgentExecution.Status.FAILED, executor.execution().status());
        assertEquals(AgentFailureReason.PERMISSION_DENIED,
                executor.execution().terminalReason().orElseThrow());
        assertEquals(List.of(fileResult), executor.execution().completedResults());
        assertExecutorStopped(executor);
    }

    @Test
    void fileDenialStopsBeforeReadingOrAdvancing() throws Exception {
        String privateContent = "private content";
        Files.writeString(root.resolve("project.md"), privateContent);
        AgentExecutor executor = new AgentExecutor(localPlan());
        executor.currentApprovalPrompt();

        assertEquals(ToolPermissionDecision.DENIED, executor.decideCurrent("deny"));

        assertEquals(AgentExecution.Status.FAILED, executor.execution().status());
        assertEquals(AgentFailureReason.PERMISSION_DENIED,
                executor.execution().terminalReason().orElseThrow());
        assertTrue(executor.execution().completedResults().isEmpty());
        assertEquals(1, executor.execution().currentStep().orElseThrow().sequence());
        assertFalse(executor.toString().contains(privateContent));
        assertExecutorStopped(executor);
    }

    @Test
    void invalidAndEndOfInputResponsesFailClosedWithoutExecution() throws Exception {
        Files.writeString(root.resolve("project.md"), "content");
        AgentExecutor invalid = new AgentExecutor(localPlan());
        invalid.currentApprovalPrompt();
        assertEquals(ToolPermissionDecision.INVALID_RESPONSE,
                invalid.decideCurrent("yes"));
        assertEquals(AgentExecution.Status.FAILED, invalid.execution().status());
        assertEquals(AgentFailureReason.INVALID_APPROVAL,
                invalid.execution().terminalReason().orElseThrow());
        assertTrue(invalid.execution().completedResults().isEmpty());
        assertExecutorStopped(invalid);

        AgentExecutor eof = new AgentExecutor(localPlan());
        eof.currentApprovalPrompt();
        assertEquals(ToolPermissionDecision.END_OF_INPUT, eof.decideCurrent(null));
        assertEquals(AgentExecution.Status.CANCELLED, eof.execution().status());
        assertEquals(AgentFailureReason.END_OF_INPUT,
                eof.execution().terminalReason().orElseThrow());
        assertTrue(eof.execution().completedResults().isEmpty());
        assertExecutorStopped(eof);
    }

    @Test
    void arrivingInterruptionCancelsEvenWhenApprovalTextSaysApprove() throws Exception {
        Files.writeString(root.resolve("project.md"), "content");
        AgentExecutor executor = new AgentExecutor(localPlan());
        executor.currentApprovalPrompt();

        Thread.currentThread().interrupt();
        try {
            assertEquals(ToolPermissionDecision.CANCELLED,
                    executor.decideCurrent("approve"));
        } finally {
            Thread.interrupted();
        }

        assertEquals(AgentExecution.Status.CANCELLED, executor.execution().status());
        assertEquals(AgentFailureReason.INTERRUPTED,
                executor.execution().terminalReason().orElseThrow());
        assertTrue(executor.execution().completedResults().isEmpty());
        assertExecutorStopped(executor);
    }

    @Test
    void explicitCancellationAndRepeatedDecisionCreateNoAuthority() throws Exception {
        Files.writeString(root.resolve("project.md"), "content");
        AgentExecutor cancelled = new AgentExecutor(localPlan());
        cancelled.currentApprovalPrompt();
        assertEquals(ToolPermissionDecision.CANCELLED, cancelled.cancelCurrent());
        assertEquals(AgentExecution.Status.CANCELLED, cancelled.execution().status());
        assertEquals(AgentFailureReason.CANCELLED,
                cancelled.execution().terminalReason().orElseThrow());
        assertExecutorStopped(cancelled);

        AgentExecutor repeated = new AgentExecutor(localPlan());
        repeated.currentApprovalPrompt();
        assertEquals(ToolPermissionDecision.APPROVED,
                repeated.decideCurrent("approve"));
        assertThrows(IllegalStateException.class,
                () -> repeated.decideCurrent("approve"));
        assertEquals(AgentExecution.Status.FAILED, repeated.execution().status());
        assertEquals(AgentFailureReason.INVALID_APPROVAL,
                repeated.execution().terminalReason().orElseThrow());
        assertTrue(repeated.execution().completedResults().isEmpty());
        assertExecutorStopped(repeated);
    }

    private AgentPlan mixedPlan() {
        return planner().plan(goal("Compare local and current guidance."),
                proposal("MIXED_EVIDENCE",
                        tool(1, "read_local_file", "{\"path\":\"project.md\"}"),
                        tool(2, "web_search", "{\"query\":\"current SearXNG guidance\"}"),
                        synthesis(3)));
    }

    private AgentPlan localPlan() {
        return planner().plan(goal("Inspect local evidence."),
                proposal("LOCAL_EVIDENCE",
                        tool(1, "read_local_file", "{\"path\":\"project.md\"}"),
                        synthesis(2)));
    }

    private AgentPlanner planner() {
        ToolRegistry registry = StandardTools.create(
                () -> new ReadLocalFilePermissionValidator(root),
                () -> new HttpGetPermissionValidator(Set.of("example.com")),
                () -> new SearxngClient("http://127.0.0.1:1"));
        return new AgentPlanner(registry);
    }

    private static AgentGoal goal(String objective) {
        return new AgentGoal(UUID.randomUUID(), objective);
    }

    private static String proposal(String need, String... steps) {
        return "{\"informationNeed\":\"" + need + "\",\"steps\":["
                + String.join(",", steps) + "]}";
    }

    private static String tool(int sequence, String name, String arguments) {
        return "{\"sequence\":" + sequence + ",\"type\":\"tool\",\"tool\":\""
                + name + "\",\"arguments\":" + arguments + "}";
    }

    private static String synthesis(int sequence) {
        return "{\"sequence\":" + sequence + ",\"type\":\"synthesis\"}";
    }

    private static void assertExecutorStopped(AgentExecutor executor) {
        AgentExecutorException prepare = assertThrows(AgentExecutorException.class,
                executor::prepareCurrent);
        assertEquals(AgentExecutorException.Reason.EXECUTION_STOPPED, prepare.reason());
        AgentExecutorException execute = assertThrows(AgentExecutorException.class,
                executor::executeCurrent);
        assertEquals(AgentExecutorException.Reason.EXECUTION_STOPPED, execute.reason());
    }
}
