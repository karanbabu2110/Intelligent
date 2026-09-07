package io.kaos.memory;

import java.util.Optional;

/** Creates the fixed answer-detail preference without overwrite. */
public interface AnswerDetailStore {
    AnswerDetail create(String key, String requestedValue);

    Optional<AnswerDetail> retrieve();
}
