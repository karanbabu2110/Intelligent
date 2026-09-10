package io.kaos.agent;

import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolResult;
import io.kaos.tool.ToolSelection;
import io.kaos.tool.permission.ToolPermissionPolicy;
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
                execution.stopCurrentTool(false);
            }
            throw failure;
        }
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
                execution.stopCurrentTool(false);
                throw new AgentExecutorException(AgentExecutorException.Reason.INVALID_TOOL_RESULT);
            }
            execution.completeCurrentTool(result);
            permission = null;
            selection = null;
            return result;
        } catch (RuntimeException | Error failure) {
            if (!execution.status().terminal()) {
                boolean cancelled = permission != null
                        && permission.snapshot().outcome()
                                == io.kaos.tool.ToolExecutionOutcome.CANCELLED;
                execution.stopCurrentTool(cancelled);
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
}
