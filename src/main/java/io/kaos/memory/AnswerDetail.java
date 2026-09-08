package io.kaos.memory;

import java.util.Locale;
import java.util.Objects;

/** The bounded values accepted by the first explicit KAOS memory. */
public enum AnswerDetail {
    CONCISE,
    BALANCED,
    DETAILED;

    public static AnswerDetail parse(String value) {
        Objects.requireNonNull(value, "value");
        return switch (value) {
            case "concise" -> CONCISE;
            case "balanced" -> BALANCED;
            case "detailed" -> DETAILED;
            default -> throw MemoryCreationException.invalidValue();
        };
    }

    public String externalValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Returns the fixed KAOS-controlled instruction for one-shot AI use. */
    public String aiInstruction() {
        return switch (this) {
            case CONCISE -> "Answer concisely and include only essential information.";
            case BALANCED ->
                    "Balance brevity with enough explanation to make the answer clear.";
            case DETAILED ->
                    "Answer in detail with relevant context and explanation.";
        };
    }
}
