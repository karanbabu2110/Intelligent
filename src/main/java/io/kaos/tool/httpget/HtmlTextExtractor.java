package io.kaos.tool.httpget;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, bounded HTML-to-readable-text extractor for untrusted publisher pages. */
final class HtmlTextExtractor {
    private static final Pattern REMOVE_BLOCK = Pattern.compile(
            "(?is)<(script|style|nav|footer|aside|form|noscript|template|svg|iframe)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern BOILERPLATE = Pattern.compile(
            "(?i)(^|[\\s_-])(advert|advertisement|cookie|consent|subscribe|newsletter|social|share|"
                    + "breadcrumb|pagination|related|recommended|promo|sponsor|banner|popup)([\\s_-]|$)");
    private static final Pattern REMOVE_BOILERPLATE = Pattern.compile(
            "(?is)<([a-z][a-z0-9]*)\\b[^>]*(?:class|id)\\s*=\\s*[\\\"'][^\\\"']*"
                    + "(?:advert|cookie|consent|subscribe|newsletter|social|share|breadcrumb|pagination|related|recommended|promo|sponsor|banner|popup)"
                    + "[^\\\"']*[\\\"'][^>]*>.*?</\\1\\s*>");
    private static final int MAX_CODE_POINTS = HttpGetResult.MAX_MODEL_TEXT_CODE_POINTS;

    private HtmlTextExtractor() { }

    static String extract(String html) {
        String withoutNoise = REMOVE_BLOCK.matcher(html).replaceAll(" ");
        withoutNoise = REMOVE_BOILERPLATE.matcher(withoutNoise).replaceAll(" ");
        var lines = new LinkedHashSet<String>();
        add(lines, TITLE.matcher(withoutNoise));
        for (String element : new String[]{"h1", "h2", "h3", "h4", "h5", "h6", "p", "li"}) {
            Pattern blocksPattern = Pattern.compile("(?is)<" + element + "\\b([^>]*)>(.*?)</" + element + "\\s*>");
            Matcher blocks = blocksPattern.matcher(withoutNoise);
            while (blocks.find()) {
                if (BOILERPLATE.matcher(blocks.group(1)).find()) continue;
                String text = clean(blocks.group(2));
                if (!text.isBlank()) lines.add(text);
                if (codePoints(lines) >= MAX_CODE_POINTS) break;
            }
            if (codePoints(lines) >= MAX_CODE_POINTS) break;
        }
        if (lines.isEmpty()) {
            String fallback = clean(withoutNoise);
            if (!fallback.isBlank()) lines.add(fallback);
        }
        String result = String.join("\n", lines);
        int end = result.offsetByCodePoints(0, Math.min(MAX_CODE_POINTS, result.codePointCount(0, result.length())));
        return result.substring(0, end).strip();
    }

    private static void add(LinkedHashSet<String> lines, Matcher matcher) {
        if (matcher.find()) {
            String value = clean(matcher.group(1));
            if (!value.isBlank()) lines.add(value);
        }
    }

    private static int codePoints(LinkedHashSet<String> lines) {
        return lines.stream().mapToInt(line -> line.codePointCount(0, line.length())).sum();
    }

    private static String clean(String value) {
        String text = TAG.matcher(value).replaceAll(" ");
        text = decode(text);
        return SPACE.matcher(text).replaceAll(" ").strip();
    }

    private static String decode(String value) {
        String result = value.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        result = numeric(result, Pattern.compile("&#x([0-9a-fA-F]+);"), 16);
        return numeric(result, Pattern.compile("&#([0-9]+);"), 10);
    }

    private static String numeric(String value, Pattern pattern, int radix) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(output,
                Matcher.quoteReplacement(codePoint(matcher.group(1), radix)));
        matcher.appendTail(output);
        return output.toString();
    }

    private static String codePoint(String value, int radix) {
        try { return new String(Character.toChars(Integer.parseInt(value, radix))); }
        catch (RuntimeException exception) { return " "; }
    }
}
