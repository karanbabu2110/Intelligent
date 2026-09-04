package io.kaos.knowledge;

import java.util.List;
import java.util.stream.IntStream;

/** One immutable bounded prompt paired with the exact contexts it contains. */
public record GroundedPrompt(String text, List<RetrievedContext> contexts) {
    public static final int MAX_CODE_POINTS = 4_096;

    public GroundedPrompt {
        if (text == null || text.isBlank()
                || text.codePointCount(0, text.length()) > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("grounded prompt must be bounded and non-blank");
        }
        if (text.codePoints().anyMatch(GroundedPrompt::unsafe)) {
            throw new IllegalArgumentException("grounded prompt contains unsafe controls");
        }
        contexts = List.copyOf(contexts);
        if (contexts.isEmpty() || contexts.size() > RelevantContextRetriever.MAX_RESULTS) {
            throw new IllegalArgumentException("grounded prompt contexts must be bounded");
        }
    }

    public int codePointCount() { return text.codePointCount(0, text.length()); }

    /** Returns stable source references in the same order as the included evidence. */
    public List<SourceCitation> citations() {
        return IntStream.range(0, contexts.size())
                .mapToObj(index -> citation(index + 1, contexts.get(index)))
                .toList();
    }

    private static SourceCitation citation(int number, RetrievedContext context) {
        return new SourceCitation(number, context.documentIdentifier(),
                context.chunk().sourceName(), context.chunk().index());
    }

    private static boolean unsafe(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t';
    }
}
