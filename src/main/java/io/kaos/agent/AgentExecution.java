package io.kaos.agent;

import io.kaos.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** In-memory state for one bounded execution of one validated plan. */
public final class AgentExecution {
    public enum Status {
        PLANNED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED;

        public boolean terminal() {
            return this == COMPLETED || this == FAILED || this == CANCELLED;
        }
    }

    public enum StepStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }
    public enum StepKind { TOOL, SYNTHESIS }

    /** Content-free observation of one step; result content is exposed separately and explicitly. */
    public record StepSnapshot(int sequence, StepKind kind, Optional<String> toolName,
            StepStatus status, boolean resultAvailable,
            Optional<AgentFailureReason> terminalReason) { }

    private final UUID id = UUID.randomUUID();
    private final AgentPlan plan;
    private final List<StepState> states;
    private final List<ToolResult<?>> completedResults = new ArrayList<>();
    private Status status = Status.PLANNED;
    private int currentIndex;
    private AgentFailureReason terminalReason;

    public AgentExecution(AgentPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        states = plan.steps().stream().map(StepState::new).toList();
    }

    public UUID id() { return id; }
    public AgentGoal goal() { return plan.goal(); }
    public AgentPlan plan() { return plan; }
    public synchronized Status status() { return status; }
    public synchronized Optional<AgentFailureReason> terminalReason() {
        return Optional.ofNullable(terminalReason);
    }

    public synchronized Optional<StepSnapshot> currentStep() {
        return currentIndex < states.size()
                ? Optional.of(states.get(currentIndex).snapshot()) : Optional.empty();
    }

    public synchronized List<StepSnapshot> steps() {
        return states.stream().map(StepState::snapshot).toList();
    }

    /** Explicit private evidence access for later synthesis; generic snapshots remain content-free. */
    public synchronized List<ToolResult<?>> completedResults() {
        return List.copyOf(completedResults);
    }

    public synchronized void beginSynthesis() {
        requireContinuable();
        StepState current = current();
        if (!(current.step instanceof AgentStep.Synthesis)
                || current.status != StepStatus.PENDING) {
            throw invalidTransition();
        }
        status = Status.RUNNING;
        current.status = StepStatus.RUNNING;
    }

    public synchronized void completeSynthesis() {
        StepState current = current();
        if (!(current.step instanceof AgentStep.Synthesis)
                || current.status != StepStatus.RUNNING) {
            throw invalidTransition();
        }
        current.status = StepStatus.COMPLETED;
        currentIndex++;
        if (currentIndex != states.size()
                || states.stream().anyMatch(state -> state.status != StepStatus.COMPLETED)) {
            throw invalidTransition();
        }
        status = Status.COMPLETED;
    }

    public synchronized void fail() {
        fail(AgentFailureReason.TOOL_EXECUTION_FAILED);
    }

    public synchronized void fail(AgentFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason.cancellation()) {
            throw invalidTransition();
        }
        terminateCurrent(Status.FAILED, StepStatus.FAILED, reason);
    }

    public synchronized void cancel() {
        cancel(AgentFailureReason.CANCELLED);
    }

    public synchronized void cancel(AgentFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!reason.cancellation()) {
            throw invalidTransition();
        }
        terminateCurrent(Status.CANCELLED, StepStatus.CANCELLED, reason);
    }

    synchronized AgentStep.Tool beginCurrentTool() {
        requireContinuable();
        StepState current = current();
        if (!(current.step instanceof AgentStep.Tool tool)
                || current.status != StepStatus.PENDING) {
            throw new AgentExecutionException(AgentExecutionException.Reason.WRONG_STEP);
        }
        status = Status.RUNNING;
        current.status = StepStatus.RUNNING;
        return tool;
    }

    synchronized void completeCurrentTool(ToolResult<?> result) {
        Objects.requireNonNull(result, "result");
        StepState current = current();
        if (!(current.step instanceof AgentStep.Tool tool)
                || current.status != StepStatus.RUNNING
                || !tool.selection().matches(result)) {
            throw invalidTransition();
        }
        current.resultAvailable = true;
        current.status = StepStatus.COMPLETED;
        completedResults.add(result);
        currentIndex++;
    }

    synchronized void stopCurrentTool(AgentFailureReason reason) {
        if (reason.cancellation()) {
            cancel(reason);
        } else {
            fail(reason);
        }
    }

    private void terminateCurrent(Status terminal, StepStatus stepTerminal,
            AgentFailureReason reason) {
        requireContinuable();
        StepState current = current();
        if (current.status != StepStatus.PENDING && current.status != StepStatus.RUNNING) {
            throw invalidTransition();
        }
        current.status = stepTerminal;
        current.terminalReason = reason;
        status = terminal;
        terminalReason = reason;
    }

    private void requireContinuable() {
        if (status.terminal()) {
            throw new AgentExecutionException(AgentExecutionException.Reason.TERMINAL_EXECUTION);
        }
    }

    private StepState current() {
        if (currentIndex >= states.size()) {
            throw invalidTransition();
        }
        return states.get(currentIndex);
    }

    private static AgentExecutionException invalidTransition() {
        return new AgentExecutionException(AgentExecutionException.Reason.INVALID_TRANSITION);
    }

    @Override
    public synchronized String toString() {
        return "AgentExecution[id=" + id + ", planId=" + plan.id() + ", status=" + status
                + ", currentSequence=" + currentStep().map(StepSnapshot::sequence).orElse(0)
                + ", completedResults=" + completedResults.size() + ", terminalReason="
                + Optional.ofNullable(terminalReason) + "]";
    }

    private static final class StepState {
        private final AgentStep step;
        private StepStatus status = StepStatus.PENDING;
        private boolean resultAvailable;
        private AgentFailureReason terminalReason;

        private StepState(AgentStep step) {
            this.step = step;
        }

        private StepSnapshot snapshot() {
            if (step instanceof AgentStep.Tool tool) {
                return new StepSnapshot(step.sequence(), StepKind.TOOL,
                        Optional.of(tool.selection().name()), status, resultAvailable,
                        Optional.ofNullable(terminalReason));
            }
            return new StepSnapshot(step.sequence(), StepKind.SYNTHESIS,
                    Optional.empty(), status, resultAvailable,
                    Optional.ofNullable(terminalReason));
        }
    }
}
