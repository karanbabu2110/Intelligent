package io.kaos.agent;

import io.kaos.tool.readlocalfile.ReadLocalFileRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Identifies goals that explicitly depend on evidence from the local KAOS project. */
public final class LocalEvidencePolicy {
    public static final String AGENT_ARCHITECTURE_PATH =
            "src/main/java/io/kaos/agent/package-info.java";
    public static final String TOOL_ARCHITECTURE_PATH =
            "src/main/java/io/kaos/tool/package-info.java";
    private static final Pattern LOCAL_INTENT = Pattern.compile(
            "\\b(?:read|inspect|review|compare|analyze|local|our)\\b");
    private static final Pattern PROJECT_CONTEXT = Pattern.compile(
            "\\b(?:kaos|epic(?:\\s+\\d+)?|repository|repo|codebase|project|"
                    + "readme(?:\\.md)?|architecture|design|implementation)\\b");
    private static final Pattern NAMED_PROJECT_FILE = Pattern.compile(
            "(?<![\\p{Alnum}_.-])([\\p{Alnum}_.-]+(?:[/\\\\][\\p{Alnum}_.-]+)*"
                    + "\\.(?:txt|md|log|java|kt|kts|gradle|json|xml|ya?ml|properties|csv))"
                    + "(?=$|[\\s,;:!?)]|[\"']|\\.(?:\\s|$))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AGENT_ARCHITECTURE = Pattern.compile(
            "\\b(?:agent (?:architecture|design|boundaries)|(?:architecture|design) of "
                    + "(?:the )?agent)\\b");
    private static final Pattern TOOL_ARCHITECTURE = Pattern.compile(
            "\\b(?:tool (?:architecture|design|boundaries)|(?:architecture|design) of "
                    + "(?:the )?tools?)\\b");

    /** Returns true only for a narrow explicit local-project evidence request. */
    public boolean isRequired(AgentGoal goal) {
        String objective = goal.objective().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        return LOCAL_INTENT.matcher(objective).find()
                && (PROJECT_CONTEXT.matcher(objective).find()
                        || NAMED_PROJECT_FILE.matcher(objective).find());
    }

    /** Returns the exact explicitly named portable project path, when one is present. */
    public Optional<String> explicitPath(AgentGoal goal) {
        var matcher = NAMED_PROJECT_FILE.matcher(goal.objective());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    /** Returns an explicit path or one of the two fixed first-agent architecture sources. */
    public Optional<String> requiredPath(AgentGoal goal) {
        Optional<String> explicit = explicitPath(goal);
        if (explicit.isPresent()) return explicit;
        String objective = goal.objective().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        if (TOOL_ARCHITECTURE.matcher(objective).find()) {
            return Optional.of(TOOL_ARCHITECTURE_PATH);
        }
        if (AGENT_ARCHITECTURE.matcher(objective).find()) {
            return Optional.of(AGENT_ARCHITECTURE_PATH);
        }
        return Optional.empty();
    }

    void validate(AgentPlan plan) {
        if (isRequired(plan.goal())
                && plan.informationNeed() != AgentPlan.InformationNeed.LOCAL_EVIDENCE
                && plan.informationNeed() != AgentPlan.InformationNeed.MIXED_EVIDENCE) {
            throw new AgentPlanningException(
                    AgentPlanningException.Reason.LOCAL_EVIDENCE_REQUIRED);
        }
        requiredPath(plan.goal()).ifPresent(expected -> {
            boolean exact = plan.steps().stream()
                    .filter(AgentStep.Tool.class::isInstance)
                    .map(AgentStep.Tool.class::cast)
                    .map(AgentStep.Tool::selection)
                    .map(selection -> selection.request(ReadLocalFileRequest.class))
                    .flatMap(Optional::stream)
                    .anyMatch(request -> expected.equals(request.path()));
            if (!exact) {
                throw new AgentPlanningException(
                        AgentPlanningException.Reason.LOCAL_EVIDENCE_MISMATCH);
            }
        });
    }
}
