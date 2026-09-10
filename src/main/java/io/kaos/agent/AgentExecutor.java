package io.kaos.agent;

import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolResult;
import io.kaos.tool.ToolSelection;
import io.kaos.tool.permission.ToolPermissionPolicy;
import java.util.Objects;

/** Sequential coordinator for the current exact tool step of one validated plan. */
public final class AgentExecutor {
    private final AgentPlan plan;
    private int currentIndex;
    private ToolSelection selection;
    private ToolPermissionPolicy<? extends ToolResult<?>> permission;
    private boolean stopped;

    public AgentExecutor(AgentPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    /** Prepares only the current step and returns the same policy until that attempt completes. */
    public synchronized ToolPermissionPolicy<? extends ToolResult<?>> prepareCurrent() {
        if (stopped) {
            throw new AgentExecutorException(AgentExecutorException.Reason.EXECUTION_STOPPED);
        }
        if (permission != null) {
            return permission;
        }
        if (currentIndex >= plan.steps().size()) {
            throw new AgentExecutorException(AgentExecutorException.Reason.PLAN_COMPLETE);
        }
        AgentStep step = plan.steps().get(currentIndex);
        if (!(step instanceof AgentStep.Tool tool)) {
            throw new AgentExecutorException(AgentExecutorException.Reason.NOT_TOOL_STEP);
        }
        selection = tool.selection();
        if (!StandardTools.LOCAL.contains(selection.name())) {
            throw new AgentExecutorException(AgentExecutorException.Reason.DISALLOWED_TOOL);
        }
        try {
            permission = selection.prepare();
            return permission;
        } catch (RuntimeException | Error failure) {
            stopped = true;
            throw failure;
        }
    }

    /** Executes the single-use concrete grant and accepts only the planned request's result. */
    public synchronized ToolResult<?> executeCurrent() {
        if (stopped) {
            throw new AgentExecutorException(AgentExecutorException.Reason.EXECUTION_STOPPED);
        }
        if (permission == null) {
            throw new AgentExecutorException(AgentExecutorException.Reason.NOT_PREPARED);
        }
        try {
            ToolResult<?> result = permission.execute();
            if (!selection.matches(result)) {
                stopped = true;
                throw new AgentExecutorException(AgentExecutorException.Reason.INVALID_TOOL_RESULT);
            }
            currentIndex++;
            permission = null;
            selection = null;
            return result;
        } catch (RuntimeException | Error failure) {
            stopped = true;
            throw failure;
        }
    }

    @Override
    public synchronized String toString() {
        String tool = selection == null ? "NOT_PREPARED" : selection.name();
        String outcome = permission == null ? "NOT_PREPARED"
                : permission.snapshot().outcome().name();
        return "AgentExecutor[planId=" + plan.id() + ", currentSequence="
                + (currentIndex + 1) + ", tool=" + tool + ", outcome=" + outcome
                + ", stopped=" + stopped + "]";
    }
}
