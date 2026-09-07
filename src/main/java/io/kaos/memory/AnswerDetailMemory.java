package io.kaos.memory;

import java.util.Objects;
import java.util.Optional;

/** Owns the absent-to-present transition for the first application-wide memory. */
public final class AnswerDetailMemory implements AnswerDetailStore {
    public static final String KEY = "answer-detail";

    private AnswerDetail value;

    @Override
    public AnswerDetail create(String key, String requestedValue) {
        AnswerDetail parsedValue = validate(key, requestedValue);
        if (value != null) {
            throw MemoryCreationException.alreadyExists();
        }
        value = parsedValue;
        return parsedValue;
    }

    @Override
    public Optional<AnswerDetail> retrieve() {
        return Optional.ofNullable(value);
    }

    static AnswerDetail validate(String key, String requestedValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestedValue, "requestedValue");
        if (!KEY.equals(key)) {
            throw MemoryCreationException.invalidKey();
        }
        return AnswerDetail.parse(requestedValue);
    }
}
