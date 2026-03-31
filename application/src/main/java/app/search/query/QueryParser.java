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

    private static final Pattern SIZE_PATTERN =
            Pattern.compile("^(\\d+)(b|kb|mb|gb)?$", Pattern.CASE_INSENSITIVE);

    public Query parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        input = input.trim();
        Map<String, String> filters = new HashMap<>();

        Matcher metaMatcher = METADATA_PATTERN.matcher(input);
        while (metaMatcher.find()) {
            String key = metaMatcher.group(1);
            String value = metaMatcher.group(2);
            if (key != null && key.equalsIgnoreCase("size")) {
                value = normalizeSizeToBytes(value);
            }
            filters.put(key, value);
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

    private String normalizeSizeToBytes(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Invalid size filter value.");
        }

        String trimmed = raw.trim();
        Matcher matcher = SIZE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid size filter value: " + raw);
        }

        long number = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || unit.isBlank() || unit.equalsIgnoreCase("b")) {
            return String.valueOf(number);
        }

        long multiplier = switch (unit.toLowerCase()) {
            case "kb" -> 1024L;
            case "mb" -> 1024L * 1024L;
            case "gb" -> 1024L * 1024L * 1024L;
            default -> throw new IllegalArgumentException("Invalid size unit: " + unit);
        };

        long bytes;
        try {
            bytes = Math.multiplyExact(number, multiplier);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Size filter value is too large: " + raw);
        }
        return String.valueOf(bytes);
    }
}