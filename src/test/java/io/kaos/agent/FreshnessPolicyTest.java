package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class FreshnessPolicyTest {
    private final FreshnessPolicy policy = new FreshnessPolicy();

    @Test
    void requiresCurrentEvidenceForExplicitRecencyAndCurrentStatePhrases() {
        for (String objective : new String[] {
                "What is the latest Java version?",
                "What is the current price of RTX 5090?",
                "What is available today?",
                "Which products are currently available?",
                "What is the best current local AI assistant?",
                "What are current security best practices for SearXNG?",
                "Who is the current president of Exampleland?",
                "What changed this month?"
        }) {
            assertEquals(FreshnessRequirement.REQUIRED, assess(objective), objective);
        }
    }

    @Test
    void recommendsCurrentEvidenceForSoftTimeSensitiveDecisions() {
        for (String objective : new String[] {
                "What Spring Boot version should I use for a new project?",
                "What is the best AI coding assistant?",
                "Summarize SearXNG security guidance.",
                "Who is the CEO of Example Corp?",
                "What legal requirements apply?",
                "When is the conference?"
        }) {
            assertEquals(FreshnessRequirement.RECOMMENDED, assess(objective), objective);
        }
    }

    @Test
    void stableExplanationsDoNotTriggerOnIsolatedDomainWords() {
        for (String objective : new String[] {
                "What is dependency injection?",
                "Explain semantic versioning.",
                "What is price elasticity?",
                "How does a security token work?",
                "Explain event sourcing."
        }) {
            assertEquals(FreshnessRequirement.NOT_REQUIRED, assess(objective), objective);
        }
    }

    private FreshnessRequirement assess(String objective) {
        return policy.assess(new AgentGoal(UUID.randomUUID(), objective));
    }
}
