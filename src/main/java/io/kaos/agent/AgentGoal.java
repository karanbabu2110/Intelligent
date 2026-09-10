package io.kaos.agent;

import java.util.UUID;

/** One immutable bounded objective for one in-memory agent run. */
public record AgentGoal(UUID id, String objective) {
    public static final int MAX_OBJECTIVE_CODE_POINTS = 4096;

    public AgentGoal {
        if (id == null) {
            throw new IllegalArgumentException("goal id must not be null");
        }
        if (objective == null) {
            throw new IllegalArgumentException("goal objective must not be null");
        }
        if (objective.isBlank()) {
            throw new IllegalArgumentException("goal objective must not be blank");
        }
        if (objective.codePointCount(0, objective.length()) > MAX_OBJECTIVE_CODE_POINTS) {
            throw new IllegalArgumentException(
                    "goal objective must contain at most "
                            + MAX_OBJECTIVE_CODE_POINTS + " characters");
        }
        if (objective.codePoints().anyMatch(AgentGoal::isUnsafeControlCharacter)) {
            throw new IllegalArgumentException(
                    "goal objective contains an unsupported control character");
        }
    }

    public int objectiveCodePointCount() {
        return objective.codePointCount(0, objective.length());
    }

    @Override
    public String toString() {
        return "AgentGoal[id=" + id + ", objectiveCodePoints="
                + objectiveCodePointCount() + "]";
    }

    private static boolean isUnsafeControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n'
                && codePoint != '\r'
                && codePoint != '\t';
    }
}
