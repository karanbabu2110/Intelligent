package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalEvidencePolicyTest {
    private final LocalEvidencePolicy policy = new LocalEvidencePolicy();

    @Test
    void explicitProjectEvidenceGoalsRequireLocalInspection() {
        for (String objective : new String[] {
                "Read README.md and summarize it.",
                "Compare KAOS's local tool architecture with current guidance.",
                "Inspect our agent implementation.",
                "Review the repository design."
        }) {
            assertTrue(policy.isRequired(goal(objective)), objective);
        }
    }

    @Test
    void stableOrPublicQuestionsDoNotInventALocalRequirement() {
        for (String objective : new String[] {
                "What is dependency injection?",
                "What is the current Java version?",
                "Explain local variables in Java.",
                "What is the current KAOS release?"
        }) {
            assertFalse(policy.isRequired(goal(objective)), objective);
        }
    }

    @Test
    void preservesAnExplicitRelativeProjectPathWithoutGuessingOne() {
        assertEquals("docs/evolution/epic-009-exit.md", policy.explicitPath(goal(
                "Read docs/evolution/epic-009-exit.md and summarize it.")).orElseThrow());
        assertEquals("README.md",
                policy.explicitPath(goal("Inspect README.md.")).orElseThrow());
        assertTrue(policy.explicitPath(goal("Inspect the local architecture.")).isEmpty());
    }

    @Test
    void routesOnlyTheTwoBoundedPathFreeArchitectureCases() {
        assertEquals(LocalEvidencePolicy.TOOL_ARCHITECTURE_PATH, policy.requiredPath(goal(
                "Compare KAOS's local tool architecture with current guidance."))
                .orElseThrow());
        assertEquals(LocalEvidencePolicy.AGENT_ARCHITECTURE_PATH, policy.requiredPath(goal(
                "Review our agent design.")).orElseThrow());
        assertTrue(policy.requiredPath(goal("Inspect the local architecture.")).isEmpty());
    }

    private static AgentGoal goal(String objective) {
        return new AgentGoal(UUID.randomUUID(), objective);
    }
}
