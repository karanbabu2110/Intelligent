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

    @Override
    public AnswerDetail edit(String key, String requestedValue) {
        AnswerDetail parsedValue = validateMutation(key, requestedValue);
        requirePresent();
        value = parsedValue;
        return parsedValue;
    }

    @Override
    public void delete(String key) {
        validateMutationKey(key);
        requirePresent();
        value = null;
    }

    static AnswerDetail validate(String key, String requestedValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(requestedValue, "requestedValue");
        if (!KEY.equals(key)) {
            throw MemoryCreationException.invalidKey();
        }
        return AnswerDetail.parse(requestedValue);
    }

    static AnswerDetail validateMutation(String key, String requestedValue) {
        validateMutationKey(key);
        Objects.requireNonNull(requestedValue, "requestedValue");
        try {
            return AnswerDetail.parse(requestedValue);
        } catch (MemoryCreationException exception) {
            throw MemoryMutationException.invalidValue();
        }
    }

    static void validateMutationKey(String key) {
        Objects.requireNonNull(key, "key");
        if (!KEY.equals(key)) {
            throw MemoryMutationException.invalidKey();
        }
    }

    private void requirePresent() {
        if (value == null) {
            throw MemoryMutationException.absent();
        }
    }
}
