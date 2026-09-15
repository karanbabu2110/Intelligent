package io.kaos.agent;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Small deterministic guard requiring web evidence for public factual questions. */
public final class PublicFactPolicy {
    private static final Pattern WHO_QUESTION = Pattern.compile("^who\\b");
    private static final Pattern PUBLIC_LIST = Pattern.compile(
            "\\b(find|list|name|identify)\\b.*"
                    + "\\b(contestants?|participants?|cast(?: members?)?)\\b");
    private static final Pattern PUBLIC_VALUE = Pattern.compile(
            "\\b(price|cost) of\\b|\\b(availability|release date|schedule)\\b");
    private static final Pattern HISTORICAL_FACT = Pattern.compile(
            "^(when|where)\\b|\\b(what|which) (year|date|version)\\b");
    private static final Pattern EXPLICIT_PUBLIC_EVIDENCE = Pattern.compile(
            "\\b(public|external) (facts?|information|evidence|guidance|sources?)\\b");

    private final FreshnessPolicy freshnessPolicy = new FreshnessPolicy();

    /** Returns true when an answer must be grounded in public search evidence. */
    public boolean isRequired(AgentGoal goal) {
        Objects.requireNonNull(goal, "goal");
        if (freshnessPolicy.assess(goal) != FreshnessRequirement.NOT_REQUIRED) {
            return true;
        }
        String objective = goal.objective().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").strip();
        return WHO_QUESTION.matcher(objective).find()
                || PUBLIC_LIST.matcher(objective).find()
                || PUBLIC_VALUE.matcher(objective).find()
                || HISTORICAL_FACT.matcher(objective).find()
                || EXPLICIT_PUBLIC_EVIDENCE.matcher(objective).find();
    }

    void validate(AgentPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (isRequired(plan.goal())
                && plan.informationNeed() != AgentPlan.InformationNeed.CURRENT_PUBLIC_EVIDENCE
                && plan.informationNeed() != AgentPlan.InformationNeed.MIXED_EVIDENCE) {
            throw new AgentPlanningException(
                    AgentPlanningException.Reason.PUBLIC_EVIDENCE_REQUIRED);
        }
    }
}
