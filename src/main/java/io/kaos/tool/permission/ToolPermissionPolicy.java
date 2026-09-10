package io.kaos.tool.permission;

import io.kaos.tool.ToolExecutionOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One validated request's lifecycle, composed around its existing concrete approval and grant. */
public final class ToolPermissionPolicy<R> {
    /** An adapter may supply an execution only after obtaining a concrete grant. */
    public record Authorization<R>(ToolPermissionDecision decision, Optional<Supplier<R>> execution) {
        public Authorization {
            Objects.requireNonNull(decision);
            Objects.requireNonNull(execution);
            if ((decision == ToolPermissionDecision.APPROVED) != execution.isPresent()) {
                throw new IllegalArgumentException("Only approval may provide execution authority.");
            }
        }
        @Override public String toString() { return "Authorization[decision=" + decision + "]"; }
    }

    /** Safe observation boundary for future history: no request, prompt, configuration or content. */
    public record Snapshot(String toolName, UUID operationId, Optional<ToolPermissionDecision> decision,
            ToolExecutionOutcome outcome, Instant startedAt, Instant updatedAt) { }

    private final String name;
    private final String prompt;
    private final Function<String, Authorization<R>> approval;
    private final Predicate<RuntimeException> cancellation;
    private final Function<ToolPermissionDecision, String> notApprovedMessage;
    private final UUID operationId = UUID.randomUUID();
    private final Instant startedAt = Instant.now();
    private Instant updatedAt = startedAt;
    private ToolPermissionDecision decision;
    private ToolExecutionOutcome outcome = ToolExecutionOutcome.APPROVAL_REQUIRED;
    private Supplier<R> execution;

    public ToolPermissionPolicy(String name, String prompt,
            Function<String, Authorization<R>> approval, Predicate<RuntimeException> cancellation) {
        this(name, prompt, approval, cancellation, decision -> "Tool request not approved. No tool was executed.");
    }

    public ToolPermissionPolicy(String name, String prompt,
            Function<String, Authorization<R>> approval, Predicate<RuntimeException> cancellation,
            Function<ToolPermissionDecision, String> notApprovedMessage) {
        this.notApprovedMessage = Objects.requireNonNull(notApprovedMessage);
        this.name = Objects.requireNonNull(name);
        this.prompt = Objects.requireNonNull(prompt);
        this.approval = Objects.requireNonNull(approval);
        this.cancellation = Objects.requireNonNull(cancellation);
    }

    public String prompt() { return prompt; }

    public synchronized String notApprovedMessage() {
        if (decision == null || decision == ToolPermissionDecision.APPROVED) {
            throw new IllegalStateException("A non-approved decision is required.");
        }
        return notApprovedMessage.apply(decision);
    }

    public synchronized ToolPermissionDecision decide(String response) {
        require(ToolExecutionOutcome.APPROVAL_REQUIRED);
        // Cancellation is checked again after input was read, including an arriving interrupt.
        if (Thread.currentThread().isInterrupted()) return cancel();
        Authorization<R> authorization;
        try {
            authorization = approval.apply(response);
            if (authorization.decision() != ToolPermissionDecision.parse(response)) {
                // An interrupt may arrive while the concrete approval is being produced.
                if (Thread.currentThread().isInterrupted()) return cancel();
                throw new IllegalStateException("Concrete approval disagrees with local-user input.");
            }
        } catch (RuntimeException | Error failure) {
            transition(ToolExecutionOutcome.FAILED);
            throw failure;
        }
        decision = authorization.decision();
        execution = authorization.execution().orElse(null);
        transition(switch (decision) {
            case APPROVED -> ToolExecutionOutcome.APPROVED;
            case DENIED -> ToolExecutionOutcome.DENIED;
            case INVALID_RESPONSE -> ToolExecutionOutcome.INVALID;
            case CANCELLED, END_OF_INPUT -> ToolExecutionOutcome.CANCELLED;
        });
        return decision;
    }

    public synchronized ToolPermissionDecision cancel() {
        require(ToolExecutionOutcome.APPROVAL_REQUIRED);
        decision = ToolPermissionDecision.CANCELLED;
        transition(ToolExecutionOutcome.CANCELLED);
        return decision;
    }

    /** Consumes authority before calling the concrete executor, even when that attempt fails. */
    public R execute() {
        Supplier<R> attempt;
        synchronized (this) {
            require(ToolExecutionOutcome.APPROVED);
            attempt = execution;
            execution = null;
            transition(ToolExecutionOutcome.EXECUTING);
        }
        try {
            R result = Objects.requireNonNull(attempt.get(), "tool result");
            synchronized (this) { transition(ToolExecutionOutcome.SUCCEEDED); }
            return result;
        } catch (RuntimeException exception) {
            synchronized (this) {
                transition(cancellation.test(exception)
                        ? ToolExecutionOutcome.CANCELLED : ToolExecutionOutcome.FAILED);
            }
            throw exception;
        } catch (Error error) {
            synchronized (this) { transition(ToolExecutionOutcome.FAILED); }
            throw error;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(name, operationId, Optional.ofNullable(decision), outcome, startedAt, updatedAt);
    }
    private void require(ToolExecutionOutcome expected) {
        if (outcome != expected) throw new IllegalStateException("Tool lifecycle is no longer at " + expected);
    }
    private void transition(ToolExecutionOutcome next) { outcome = next; updatedAt = Instant.now(); }
    @Override public synchronized String toString() { return snapshot().toString(); }
}
