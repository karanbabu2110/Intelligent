package io.kaos.agent;

import io.kaos.tool.ToolResult;
import io.kaos.tool.websearch.WebSearchResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable truthful summary of one terminal bounded agent execution. */
public final class AgentResult {
    public static final int MAX_ANSWER_CODE_POINTS = 65_536;

    public enum CurrentEvidenceStatus {
        NOT_REQUIRED,
        VERIFIED,
        REQUIRED_BUT_UNAVAILABLE
    }

    /** Public source reference retained from one verified bounded search result. */
    public record SourceReference(String title, String url) {
        public SourceReference {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(url, "url");
            if (title.isBlank() || url.isBlank()) {
                throw new IllegalArgumentException("source reference must not be blank");
            }
        }

        @Override public String toString() { return "SourceReference[REDACTED]"; }
    }

    private final UUID executionId;
    private final AgentGoal goal;
    private final AgentExecution.Status status;
    private final List<AgentExecution.StepSnapshot> completedSteps;
    private final Optional<AgentExecution.StepSnapshot> terminalStep;
    private final List<ToolResult<?>> evidence;
    private final List<SourceReference> currentSources;
    private final CurrentEvidenceStatus currentEvidenceStatus;
    private final Optional<AgentFailureReason> terminalReason;
    private final Optional<String> answer;

    private AgentResult(AgentExecution execution, Optional<String> answer) {
        executionId = execution.id();
        goal = execution.goal();
        status = execution.status();
        List<AgentExecution.StepSnapshot> steps = execution.steps();
        completedSteps = steps.stream()
                .filter(step -> step.status() == AgentExecution.StepStatus.COMPLETED)
                .toList();
        terminalStep = steps.stream().filter(step ->
                step.status() == AgentExecution.StepStatus.FAILED
                        || step.status() == AgentExecution.StepStatus.CANCELLED).findFirst();
        evidence = execution.completedResults();
        currentSources = evidence.stream()
                .filter(WebSearchResult.class::isInstance)
                .map(WebSearchResult.class::cast)
                .flatMap(result -> result.results().stream())
                .map(entry -> new SourceReference(entry.title(), entry.url()))
                .toList();
        currentEvidenceStatus = currentEvidenceStatus(execution);
        terminalReason = execution.terminalReason();
        this.answer = answer;
    }

    /** Creates a result only after all validated steps, including synthesis, completed. */
    public static AgentResult completed(AgentExecution execution, String answer) {
        Objects.requireNonNull(execution, "execution");
        if (execution.status() != AgentExecution.Status.COMPLETED) {
            throw new IllegalArgumentException("completed result requires completed execution");
        }
        return new AgentResult(execution, Optional.of(validateAnswer(answer)));
    }

    /** Creates an answer-free partial result for one failed or cancelled execution. */
    public static AgentResult incomplete(AgentExecution execution) {
        Objects.requireNonNull(execution, "execution");
        if (execution.status() != AgentExecution.Status.FAILED
                && execution.status() != AgentExecution.Status.CANCELLED) {
            throw new IllegalArgumentException("incomplete result requires terminal execution");
        }
        return new AgentResult(execution, Optional.empty());
    }

    public UUID executionId() { return executionId; }
    public AgentGoal goal() { return goal; }
    public AgentExecution.Status status() { return status; }
    public List<AgentExecution.StepSnapshot> completedSteps() { return completedSteps; }
    public Optional<AgentExecution.StepSnapshot> terminalStep() { return terminalStep; }

    /** Returns the already bounded evidence explicitly, never through generic diagnostics. */
    public List<ToolResult<?>> evidence() { return evidence; }

    public List<SourceReference> currentSources() { return currentSources; }
    public CurrentEvidenceStatus currentEvidenceStatus() { return currentEvidenceStatus; }
    public Optional<AgentFailureReason> terminalReason() { return terminalReason; }
    public Optional<String> answer() { return answer; }

    public boolean successful() { return status == AgentExecution.Status.COMPLETED; }

    @Override
    public String toString() {
        return "AgentResult[executionId=" + executionId + ", status=" + status
                + ", completedSteps=" + completedSteps.size() + ", evidence="
                + evidence.size() + ", currentSources=" + currentSources.size()
                + ", currentEvidenceStatus=" + currentEvidenceStatus
                + ", terminalReason=" + terminalReason + ", answerAvailable="
                + answer.isPresent() + "]";
    }

    private static CurrentEvidenceStatus currentEvidenceStatus(AgentExecution execution) {
        boolean required = execution.plan().informationNeed()
                == AgentPlan.InformationNeed.CURRENT_PUBLIC_EVIDENCE
                || execution.plan().informationNeed() == AgentPlan.InformationNeed.MIXED_EVIDENCE;
        if (!required) {
            return CurrentEvidenceStatus.NOT_REQUIRED;
        }
        boolean verified = execution.completedResults().stream()
                .filter(WebSearchResult.class::isInstance)
                .map(WebSearchResult.class::cast)
                .anyMatch(result -> !result.results().isEmpty());
        return verified ? CurrentEvidenceStatus.VERIFIED
                : CurrentEvidenceStatus.REQUIRED_BUT_UNAVAILABLE;
    }

    private static String validateAnswer(String answer) {
        if (answer == null) {
            throw new IllegalArgumentException("agent answer must not be null");
        }
        String normalized = answer.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("agent answer must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > MAX_ANSWER_CODE_POINTS) {
            throw new IllegalArgumentException("agent answer exceeds the bounded response size");
        }
        if (normalized.codePoints().anyMatch(AgentResult::unsafeControl)) {
            throw new IllegalArgumentException("agent answer contains an unsupported control character");
        }
        return normalized;
    }

    private static boolean unsafeControl(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t';
    }
}
