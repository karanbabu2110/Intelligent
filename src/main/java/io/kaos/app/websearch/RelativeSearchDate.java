package io.kaos.app.websearch;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Resolves the relative dates that a public search query must preserve. */
public record RelativeSearchDate(List<LocalDate> required) {
    private static final Pattern TODAY = Pattern.compile("(?i)\\btoday\\b");
    private static final Pattern YESTERDAY = Pattern.compile("(?i)\\byesterday\\b");

    public RelativeSearchDate {
        required = List.copyOf(required);
    }

    public static RelativeSearchDate from(String question, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        var dates = new ArrayList<LocalDate>();
        if (TODAY.matcher(question).find()) dates.add(today);
        if (YESTERDAY.matcher(question).find()) dates.add(today.minusDays(1));
        return new RelativeSearchDate(dates);
    }

    public String instruction(Clock clock) {
        if (required.isEmpty()) return "";
        return " Current local date is " + LocalDate.now(clock) + " (" + clock.getZone().getId()
                + "). For this question, include each resolved ISO date in the web_search query: "
                + required + ". Do not substitute another date or year. "
                + "Build the query using only words from the user's question plus the ISO date; "
                + "do not add an agency, event detail, or synonym.";
    }

    public boolean matches(String question, String query) {
        if (required.isEmpty()) return true;
        String queryLower = query.toLowerCase(Locale.ROOT);
        if (!required.stream().allMatch(date -> queryLower.contains(date.toString()))) return false;
        String normalized = queryLower;
        for (LocalDate date : required) normalized = normalized.replace(date.toString(), " ");
        Set<String> original = words(question);
        return original.containsAll(words(normalized));
    }

    private static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isEmpty()).collect(Collectors.toSet());
    }
}
