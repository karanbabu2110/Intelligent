package io.kaos.memory;

/** Creates the fixed answer-detail preference without overwrite. */
@FunctionalInterface
public interface AnswerDetailStore {
    AnswerDetail create(String key, String requestedValue);
}
