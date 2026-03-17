package app.search.query;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryParser {
    
    private static final Pattern METADATA_PATTERN =
            Pattern.compile("^([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.-]+)$");

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]+\\.[a-zA-Z0-9]+$");

    public Query parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        input = input.trim();

        Matcher metaMatcher = METADATA_PATTERN.matcher(input);
        if (metaMatcher.matches()) {
            String key = metaMatcher.group(1);
            String value = metaMatcher.group(2);

            return new Query(QueryType.METADATA, value, key);
        }

        if (FILENAME_PATTERN.matcher(input).matches()) {
            return new Query(QueryType.FILENAME, input, null);
        }

        return new Query(QueryType.FULLTEXT, input, null);
    }
}
