package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicFactPolicyTest {
    private final PublicFactPolicy policy = new PublicFactPolicy();

    @Test
    void requiresSearchForPublicFactsThatMayBeMissingOrStaleInModelMemory() {
        for (String objective : new String[] {
                "Who are the contestants of Bigg Boss Tamil season 10?",
                "Who is the current CEO of Example Corp?",
                "What is the latest Spring Boot version?",
                "What is the current price of RTX 5090?",
                "When was Example Corp founded?"
        }) {
            assertTrue(policy.isRequired(goal(objective)), objective);
        }
    }

    @Test
    void stableExplanationsAndLocalOnlyQuestionsDoNotRequirePublicEvidence() {
        for (String objective : new String[] {
                "What is dependency injection?",
                "Explain polymorphism.",
                "Explain participant observation.",
                "What does README.md say?"
        }) {
            assertFalse(policy.isRequired(goal(objective)), objective);
        }
    }

    @Test
    void mixedComparisonRequiresPublicEvidenceAsWellAsLocalEvidence() {
        assertTrue(policy.isRequired(
                goal("Compare README architecture with current public guidance.")));
    }

    private static AgentGoal goal(String objective) {
        return new AgentGoal(UUID.randomUUID(), objective);
    }
}
