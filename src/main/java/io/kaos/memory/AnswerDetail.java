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
}
