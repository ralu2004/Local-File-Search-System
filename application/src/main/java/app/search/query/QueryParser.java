package app.search.query;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses user search input into a structured {@link Query}.
 * <p>
 * Supports full-text terms, filename-style queries, and metadata-style filters
 * like {@code ext:...}, {@code modified:...}, and {@code size:...}.
 */
public class QueryParser {

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.-]+)");

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]+\\.[a-zA-Z0-9]{2,}$");

    public Query parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        input = input.trim();
        Map<String, String> filters = new HashMap<>();

        Matcher metaMatcher = METADATA_PATTERN.matcher(input);
        while (metaMatcher.find()) {
            filters.put(metaMatcher.group(1), metaMatcher.group(2));
        }

        String remaining = metaMatcher.reset(input)
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();

        if (filters.isEmpty()) {
            if (FILENAME_PATTERN.matcher(remaining).matches()) {
                return new Query(QueryType.FILENAME, remaining, Map.of());
            }
            return new Query(QueryType.FULLTEXT, remaining, Map.of());
        }

        if (remaining.isEmpty()) {
            return new Query(QueryType.METADATA, null, Map.copyOf(filters));
        }

        return new Query(QueryType.MIXED, remaining, Map.copyOf(filters));
    }
}