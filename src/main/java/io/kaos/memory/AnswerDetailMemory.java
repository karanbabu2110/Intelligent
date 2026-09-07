package io.kaos.memory;

import java.util.Objects;

/** Owns the absent-to-present transition for the first application-wide memory. */
public final class AnswerDetailMemory {
    public static final String KEY = "answer-detail";

    private AnswerDetail value;

    public AnswerDetail create(String key, String requestedValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestedValue, "requestedValue");
        if (!KEY.equals(key)) {
            throw MemoryCreationException.invalidKey();
        }
        AnswerDetail parsedValue = AnswerDetail.parse(requestedValue);
        if (value != null) {
            throw MemoryCreationException.alreadyExists();
        }
        value = parsedValue;
        return parsedValue;
    }
}
