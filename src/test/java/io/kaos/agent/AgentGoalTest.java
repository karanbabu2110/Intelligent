package io.kaos.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentGoalTest {
    @Test
    void preservesOneBoundedUnicodeObjectiveExactly() {
        UUID id = UUID.fromString("01993dc9-2b00-7000-8000-000000000901");
        String objective = "  Compare local KAOS with current guidance \ud83c\udf0d  ";

        AgentGoal goal = new AgentGoal(id, objective);

        assertEquals(id, goal.id());
        assertEquals(objective, goal.objective());
        assertEquals(objective.codePointCount(0, objective.length()),
                goal.objectiveCodePointCount());
    }

    @Test
    void acceptsTheExactCodePointBoundary() {
        String objective = "\ud83c\udf0d".repeat(AgentGoal.MAX_OBJECTIVE_CODE_POINTS);

        AgentGoal goal = new AgentGoal(UUID.randomUUID(), objective);

        assertEquals(AgentGoal.MAX_OBJECTIVE_CODE_POINTS,
                goal.objectiveCodePointCount());
    }

    @Test
    void rejectsMissingBlankOversizedAndUnsafeValues() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new AgentGoal(null, "goal"));
        assertThrows(IllegalArgumentException.class, () -> new AgentGoal(id, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentGoal(id, " \t\r\n"));
        assertThrows(IllegalArgumentException.class, () -> new AgentGoal(id,
                "a".repeat(AgentGoal.MAX_OBJECTIVE_CODE_POINTS + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentGoal(id, "inspect\u0000private"));
    }

    @Test
    void genericDiagnosticsDoNotExposeTheObjective() {
        String privateObjective = "inspect private-project-plan.md";
        AgentGoal goal = new AgentGoal(UUID.randomUUID(), privateObjective);

        assertFalse(goal.toString().contains(privateObjective));
        assertEquals("AgentGoal[id=" + goal.id() + ", objectiveCodePoints="
                + goal.objectiveCodePointCount() + "]", goal.toString());
    }
}
