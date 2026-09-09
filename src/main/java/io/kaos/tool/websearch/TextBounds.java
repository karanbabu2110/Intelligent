package io.kaos.tool.websearch;

/** Unicode and terminal-safe text validation within this concrete tool. */
final class TextBounds {
    private TextBounds() { }
    static void check(String value, int maximum) {
        if (value == null || value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                        || Character.getType(cp) == Character.FORMAT
                        || (cp >= 0xd800 && cp <= 0xdfff))) {
            throw new WebSearchException(WebSearchException.Reason.INVALID_REQUEST);
        }
    }
}
