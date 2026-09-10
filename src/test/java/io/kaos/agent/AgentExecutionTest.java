package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import io.kaos.tool.readlocalfile.ReadLocalFileResult;
import io.kaos.tool.websearch.SearxngClient;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentExecutionTest {
    @TempDir Path root;

    @Test
    void startsPlannedWithOneObservablePendingStatePerPlanStep() {
        AgentPlan plan = mixedPlan();
        AgentExecution execution = new AgentExecution(plan);

        assertSame(plan, execution.plan());
        assertSame(plan.goal(), execution.goal());
        assertEquals(AgentExecution.Status.PLANNED, execution.status());
        assertEquals(1, execution.currentStep().orElseThrow().sequence());
        assertEquals(List.of(AgentExecution.StepStatus.PENDING,
                        AgentExecution.StepStatus.PENDING, AgentExecution.StepStatus.PENDING),
                statuses(execution));
        assertTrue(execution.completedResults().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> execution.steps().clear());
    }

    @Test
    void completesEveryStepInOrderAndOnlyThenCompletesExecution() {
        AgentExecution execution = new AgentExecution(mixedPlan());

        AgentStep.Tool fileStep = execution.beginCurrentTool();
        ReadLocalFileRequest fileRequest = fileStep.selection()
                .request(ReadLocalFileRequest.class).orElseThrow();
        ReadLocalFileResult fileResult = new ReadLocalFileResult(fileRequest, "local evidence");
        execution.completeCurrentTool(fileResult);

        assertEquals(AgentExecution.Status.RUNNING, execution.status());
        assertEquals(2, execution.currentStep().orElseThrow().sequence());
        assertEquals(List.of(AgentExecution.StepStatus.COMPLETED,
                        AgentExecution.StepStatus.PENDING, AgentExecution.StepStatus.PENDING),
                statuses(execution));
        assertTrue(execution.steps().getFirst().resultAvailable());

        AgentStep.Tool searchStep = execution.beginCurrentTool();
        WebSearchRequest searchRequest = searchStep.selection()
                .request(WebSearchRequest.class).orElseThrow();
        WebSearchResult searchResult = new WebSearchResult(searchRequest, List.of(
                new WebSearchResult.Entry("Current", "https://example.com/current", "evidence")));
        execution.completeCurrentTool(searchResult);

        assertEquals(3, execution.currentStep().orElseThrow().sequence());
        execution.beginSynthesis();
        execution.completeSynthesis();

        assertEquals(AgentExecution.Status.COMPLETED, execution.status());
        assertTrue(execution.currentStep().isEmpty());
        assertEquals(List.of(AgentExecution.StepStatus.COMPLETED,
                        AgentExecution.StepStatus.COMPLETED, AgentExecution.StepStatus.COMPLETED),
                statuses(execution));
        assertEquals(List.of(fileResult, searchResult), execution.completedResults());
        assertThrows(UnsupportedOperationException.class,
                () -> execution.completedResults().clear());
        assertExecutionReason(AgentExecutionException.Reason.TERMINAL_EXECUTION,
                execution::beginSynthesis);
    }

    @Test
    void laterFailurePreservesCompletedEvidenceAndPendingLaterSteps() {
        AgentExecution execution = new AgentExecution(mixedPlan());
        AgentStep.Tool fileStep = execution.beginCurrentTool();
        ReadLocalFileResult fileResult = new ReadLocalFileResult(fileStep.selection()
                .request(ReadLocalFileRequest.class).orElseThrow(), "preserved evidence");
        execution.completeCurrentTool(fileResult);
        execution.beginCurrentTool();

        execution.fail();

        assertEquals(AgentExecution.Status.FAILED, execution.status());
        assertEquals(List.of(AgentExecution.StepStatus.COMPLETED,
                        AgentExecution.StepStatus.FAILED, AgentExecution.StepStatus.PENDING),
                statuses(execution));
        assertEquals(List.of(fileResult), execution.completedResults());
        assertEquals(2, execution.currentStep().orElseThrow().sequence());
        assertExecutionReason(AgentExecutionException.Reason.TERMINAL_EXECUTION,
                execution::beginCurrentTool);
        assertExecutionReason(AgentExecutionException.Reason.TERMINAL_EXECUTION,
                execution::beginSynthesis);
    }

    @Test
    void cancellationBeforeOrDuringAstepIsTerminal() {
        AgentExecution planned = new AgentExecution(mixedPlan());
        planned.cancel();
        assertEquals(AgentExecution.Status.CANCELLED, planned.status());
        assertEquals(AgentExecution.StepStatus.CANCELLED,
                planned.currentStep().orElseThrow().status());
        assertExecutionReason(AgentExecutionException.Reason.TERMINAL_EXECUTION,
                planned::beginCurrentTool);

        AgentExecution running = new AgentExecution(mixedPlan());
        running.beginCurrentTool();
        running.cancel();
        assertEquals(AgentExecution.Status.CANCELLED, running.status());
        assertEquals(List.of(AgentExecution.StepStatus.CANCELLED,
                        AgentExecution.StepStatus.PENDING, AgentExecution.StepStatus.PENDING),
                statuses(running));
    }

    @Test
    void rejectsOutOfOrderWrongResultAndPrematureCompletionTransitions() {
        AgentExecution execution = new AgentExecution(mixedPlan());
        assertExecutionReason(AgentExecutionException.Reason.INVALID_TRANSITION,
                execution::beginSynthesis);
        assertExecutionReason(AgentExecutionException.Reason.INVALID_TRANSITION,
                execution::completeSynthesis);
        AgentStep.Tool current = execution.beginCurrentTool();
        ReadLocalFileRequest request = current.selection()
                .request(ReadLocalFileRequest.class).orElseThrow();
        assertExecutionReason(AgentExecutionException.Reason.INVALID_TRANSITION,
                () -> execution.completeCurrentTool(
                        new ReadLocalFileResult(new ReadLocalFileRequest("different.md"), "wrong")));
        execution.completeCurrentTool(new ReadLocalFileResult(request, "right"));
        assertEquals(2, execution.beginCurrentTool().sequence());
        assertExecutionReason(AgentExecutionException.Reason.INVALID_TRANSITION,
                () -> execution.completeCurrentTool(new ReadLocalFileResult(request, "old")));
    }

    @Test
    void synthesisOnlyExecutionCompletesWithoutToolEvidence() {
        AgentPlan plan = planner().plan(goal("What is dependency injection?"),
                proposal("STABLE_INTERNAL", synthesis(1)));
        AgentExecution execution = new AgentExecution(plan);

        execution.beginSynthesis();
        execution.completeSynthesis();

        assertEquals(AgentExecution.Status.COMPLETED, execution.status());
        assertTrue(execution.completedResults().isEmpty());
    }

    @Test
    void genericDiagnosticsExcludeGoalAndCompletedEvidence() {
        String privateGoal = "inspect private architecture";
        String privateEvidence = "private local evidence";
        AgentPlan plan = planner().plan(goal(privateGoal), proposal("LOCAL_EVIDENCE",
                tool(1, "read_local_file", "{\"path\":\"private.md\"}"), synthesis(2)));
        AgentExecution execution = new AgentExecution(plan);
        AgentStep.Tool step = execution.beginCurrentTool();
        execution.completeCurrentTool(new ReadLocalFileResult(step.selection()
                .request(ReadLocalFileRequest.class).orElseThrow(), privateEvidence));

        assertFalse(execution.toString().contains(privateGoal));
        assertFalse(execution.toString().contains(privateEvidence));
        assertFalse(execution.steps().toString().contains("private.md"));
    }

    private AgentPlan mixedPlan() {
        return planner().plan(goal("Compare local and current evidence."),
                proposal("MIXED_EVIDENCE",
                        tool(1, "read_local_file", "{\"path\":\"project.md\"}"),
                        tool(2, "web_search", "{\"query\":\"current guidance\"}"),
                        synthesis(3)));
    }

    private AgentPlanner planner() {
        ToolRegistry registry = StandardTools.create(
                () -> new ReadLocalFilePermissionValidator(root),
                () -> new HttpGetPermissionValidator(java.util.Set.of("example.com")),
                () -> new SearxngClient("http://127.0.0.1:1"));
        return new AgentPlanner(registry);
    }

    private static AgentGoal goal(String objective) {
        return new AgentGoal(UUID.randomUUID(), objective);
    }

    private static List<AgentExecution.StepStatus> statuses(AgentExecution execution) {
        return execution.steps().stream().map(AgentExecution.StepSnapshot::status).toList();
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

    private static void assertExecutionReason(AgentExecutionException.Reason reason,
            org.junit.jupiter.api.function.Executable action) {
        AgentExecutionException exception = assertThrows(AgentExecutionException.class, action);
        assertEquals(reason, exception.reason());
    }
}
