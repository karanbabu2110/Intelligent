package io.kaos.agent;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Small deterministic guard against omitting clearly time-sensitive evidence. */
public final class FreshnessPolicy {
    private static final Pattern EXPLICIT_RECENCY = Pattern.compile(
            "\\b(latest|currently|today|recent|right now|as of|this week|this month)\\b");
    private static final Pattern EXPLICIT_CURRENT_CONTEXT = Pattern.compile(
            "\\b(best current|current (price|version|release|availability|security|guidance|"
                    + "best practices?|recommendations?|law|rules?|regulations?|schedule|"
                    + "events?|ceo|president|prime minister|governor|chair))\\b");
    private static final Pattern AVAILABLE_NOW = Pattern.compile(
            "\\b(available (now|today)|available right now)\\b");
    private static final Pattern QUALIFIED_CURRENT_SECURITY_GUIDANCE = Pattern.compile(
            "\\bcurrent(?: [\\p{Alnum}_.+-]+){1,3} security "
                    + "(?:guidance|best practices?|recommendations?)\\b");
    private static final Pattern RECOMMENDATION_INTENT = Pattern.compile(
            "\\b(should (i|we) use|recommend(ed|ation)?|best)\\b");
    private static final Pattern TECHNOLOGY_OR_PRODUCT = Pattern.compile(
            "\\b(version|software|framework|library|tool|assistant|platform|product|device|"
                    + "laptop|gpu|coding)\\b");
    private static final Pattern SECURITY_GUIDANCE = Pattern.compile(
            "\\bsecurity (guidance|best practices?|recommendations?)\\b");
    private static final Pattern PURCHASE_DECISION = Pattern.compile(
            "\\b(should (i|we) buy|worth buying|purchase recommendation)\\b");
    private static final Pattern PUBLIC_ENTITY_STATE = Pattern.compile(
            "\\bwho is (the )?(ceo|president|prime minister|governor|chair)\\b");
    private static final Pattern LAW_OR_RULE = Pattern.compile(
            "\\b(legal requirements?|laws?|regulations?|compliance rules?)\\b");
    private static final Pattern SCHEDULE_OR_EVENT = Pattern.compile(
            "\\b(schedule for|when (is|does|will) (the )?(event|game|release|conference|"
                    + "meeting))\\b");

    public FreshnessRequirement assess(AgentGoal goal) {
        Objects.requireNonNull(goal, "goal");
        String objective = normalize(goal.objective());
        if (EXPLICIT_RECENCY.matcher(objective).find()
                || EXPLICIT_CURRENT_CONTEXT.matcher(objective).find()
                || AVAILABLE_NOW.matcher(objective).find()
                || QUALIFIED_CURRENT_SECURITY_GUIDANCE.matcher(objective).find()) {
            return FreshnessRequirement.REQUIRED;
        }
        if ((RECOMMENDATION_INTENT.matcher(objective).find()
                && TECHNOLOGY_OR_PRODUCT.matcher(objective).find())
                || SECURITY_GUIDANCE.matcher(objective).find()
                || PURCHASE_DECISION.matcher(objective).find()
                || PUBLIC_ENTITY_STATE.matcher(objective).find()
                || LAW_OR_RULE.matcher(objective).find()
                || SCHEDULE_OR_EVENT.matcher(objective).find()) {
            return FreshnessRequirement.RECOMMENDED;
        }
        return FreshnessRequirement.NOT_REQUIRED;
    }

    void validate(AgentPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.freshnessRequirement() == FreshnessRequirement.REQUIRED
                && plan.informationNeed() != AgentPlan.InformationNeed.CURRENT_PUBLIC_EVIDENCE
                && plan.informationNeed() != AgentPlan.InformationNeed.MIXED_EVIDENCE) {
            throw new AgentPlanningException(
                    AgentPlanningException.Reason.FRESHNESS_REQUIRED);
        }
    }

    private static String normalize(String objective) {
        return objective.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
