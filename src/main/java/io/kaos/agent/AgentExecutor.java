package io.kaos.agent;

import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolResult;
import io.kaos.tool.ToolSelection;
import io.kaos.tool.permission.ToolPermissionDecision;
import io.kaos.tool.permission.ToolPermissionPolicy;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionException;
import io.kaos.tool.websearch.WebSearchException;
import java.util.Objects;

/** Sequential coordinator for the current exact tool step of one validated plan. */
public final class AgentExecutor {
    private final AgentExecution execution;
    private ToolSelection selection;
    private ToolPermissionPolicy<? extends ToolResult<?>> permission;

    public AgentExecutor(AgentPlan plan) {
        this(new AgentExecution(plan));
    }

    public AgentExecutor(AgentExecution execution) {
        this.execution = Objects.requireNonNull(execution, "execution");
    }

    public AgentExecution execution() { return execution; }

    /** Prepares only the current step and returns the same policy until that attempt completes. */
    public synchronized ToolPermissionPolicy<? extends ToolResult<?>> prepareCurrent() {
        if (execution.status().terminal()) {
            throw new AgentExecutorException(AgentExecutorException.Reason.EXECUTION_STOPPED);
        }
        if (permission != null) {
            return permission;
        }
        AgentStep.Tool tool;
        try {
            tool = execution.beginCurrentTool();
        } catch (AgentExecutionException exception) {
            throw map(exception);
        }
        selection = tool.selection();
        if (!StandardTools.LOCAL.contains(selection.name())) {
            throw new AgentExecutorException(AgentExecutorException.Reason.DISALLOWED_TOOL);
        }
        try {
            permission = selection.prepare();
            return permission;
        } catch (RuntimeException | Error failure) {
            if (!execution.status().terminal()) {
                execution.stopCurrentTool(classifyPreparation(failure));
            }
            throw failure;
        }
    }

    /** Returns the concrete tool's exact approval prompt without compressing its disclosure. */
    public synchronized String currentApprovalPrompt() {
        return prepareCurrent().prompt();
    }

    /** Delegates one response to the current concrete policy and stops on every non-approval. */
    public synchronized ToolPermissionDecision decideCurrent(String response) {
        if (execution.status().terminal()) {
            throw new AgentExecutorException(AgentExecutorException.Reason.EXECUTION_STOPPED);
        }
        if (permission == null) {
            throw new AgentExecutorException(AgentExecutorException.Reason.NOT_PREPARED);
        }
        try {
            ToolPermissionDecision decision = permission.decide(response);
            if (decision != ToolPermissionDecision.APPROVED) {
                execution.stopCurrentTool(reason(decision));
            }
            return decision;
        } catch (RuntimeException | Error failure) {
            if (!execution.status().terminal()) {
                execution.stopCurrentTool(AgentFailureReason.INVALID_APPROVAL);
            }
            throw failure;
        }
    }

    /** Cancels the current concrete policy without creating a grant. */
    public synchronized ToolPermissionDecision cancelCurrent() {
        if (execution.status().terminal()) {
            throw new AgentExecutorException(AgentExecutorException.Reason.EXECUTION_STOPPED);
        }
        if (permission == null) {
            throw new AgentExecutorException(AgentExecutorException.Reason.NOT_PREPARED);
        }
        ToolPermissionDecision decision = permission.cancel();
        execution.stopCurrentTool(AgentFailureReason.CANCELLED);
        return decision;
    }

    /** Executes the single-use concrete grant and accepts only the planned request's result. */
    public synchronized ToolResult<?> executeCurrent() {
        if (execution.status().terminal()) {
            throw new AgentExecutorException(AgentExecutorException.Reason.EXECUTION_STOPPED);
        }
        if (permission == null) {
            throw new AgentExecutorException(AgentExecutorException.Reason.NOT_PREPARED);
        }
        try {
            ToolResult<?> result = permission.execute();
            if (!selection.matches(result)) {
                execution.stopCurrentTool(AgentFailureReason.INVALID_TOOL_RESULT);
                throw new AgentExecutorException(AgentExecutorException.Reason.INVALID_TOOL_RESULT);
            }
            execution.completeCurrentTool(result);
            permission = null;
            selection = null;
            return result;
        } catch (RuntimeException | Error failure) {
            if (!execution.status().terminal()) {
                execution.stopCurrentTool(classifyExecution(failure));
            }
            throw failure;
        }
    }

    @Override
    public synchronized String toString() {
        String tool = selection == null ? "NOT_PREPARED" : selection.name();
        String outcome = permission == null ? "NOT_PREPARED"
                : permission.snapshot().outcome().name();
        return "AgentExecutor[executionId=" + execution.id() + ", currentSequence="
                + execution.currentStep().map(AgentExecution.StepSnapshot::sequence).orElse(0)
                + ", tool=" + tool + ", outcome=" + outcome
                + ", status=" + execution.status() + "]";
    }

    private static AgentExecutorException map(AgentExecutionException exception) {
        AgentExecutorException.Reason reason = switch (exception.reason()) {
            case WRONG_STEP, INVALID_TRANSITION -> AgentExecutorException.Reason.NOT_TOOL_STEP;
            case TERMINAL_EXECUTION -> AgentExecutorException.Reason.EXECUTION_STOPPED;
        };
        return new AgentExecutorException(reason);
    }

    private static AgentFailureReason reason(ToolPermissionDecision decision) {
        return switch (decision) {
            case DENIED -> AgentFailureReason.PERMISSION_DENIED;
            case INVALID_RESPONSE -> AgentFailureReason.INVALID_APPROVAL;
            case END_OF_INPUT -> AgentFailureReason.END_OF_INPUT;
            case CANCELLED -> Thread.currentThread().isInterrupted()
                    ? AgentFailureReason.INTERRUPTED : AgentFailureReason.CANCELLED;
            case APPROVED -> throw new IllegalArgumentException("approval is not a failure");
        };
    }

    private static AgentFailureReason classifyPreparation(Throwable failure) {
        if (failure instanceof ReadLocalFilePermissionException exception) {
            return exception.reason() == ReadLocalFilePermissionException.Reason.INVALID_CONFIGURATION
                    ? AgentFailureReason.TOOL_CONFIGURATION_UNAVAILABLE
                    : AgentFailureReason.TOOL_VALIDATION_FAILED;
        }
        if (failure instanceof WebSearchException exception
                && (exception.reason() == WebSearchException.Reason.SEARCH_SERVICE_NOT_CONFIGURED
                || exception.reason() == WebSearchException.Reason.INVALID_CONFIGURATION)) {
            return AgentFailureReason.TOOL_CONFIGURATION_UNAVAILABLE;
        }
        return AgentFailureReason.TOOL_VALIDATION_FAILED;
    }

    private AgentFailureReason classifyExecution(Throwable failure) {
        var snapshot = permission == null ? null : permission.snapshot();
        if (snapshot != null && snapshot.decision().isPresent()
                && snapshot.decision().orElseThrow() != ToolPermissionDecision.APPROVED) {
            return reason(snapshot.decision().orElseThrow());
        }
        if (snapshot != null && snapshot.outcome()
                == io.kaos.tool.ToolExecutionOutcome.CANCELLED) {
            return Thread.currentThread().isInterrupted()
                    ? AgentFailureReason.INTERRUPTED : AgentFailureReason.CANCELLED;
        }
        return failure instanceof AgentExecutorException exception
                && exception.reason() == AgentExecutorException.Reason.INVALID_TOOL_RESULT
                ? AgentFailureReason.INVALID_TOOL_RESULT
                : AgentFailureReason.TOOL_EXECUTION_FAILED;
    }
}
