package app.search.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses user search input into a structured {@link Query}.
 * <p>
 * Supports full-text terms, filename-style queries, and metadata-style filters
 * like {@code ext:...}, {@code modified:...}, {@code size:...},
 * {@code path:...}, and {@code content:...}.
 * <p>
 * Duplicate qualifiers are combined using the AND operator.
 * Qualifier order does not affect the result.
 */
public class QueryParser {

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("([a-zA-Z0-9_.-]+):([^\\s]+)");

    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]+\\.[a-zA-Z0-9]{2,}$");

    private static final Pattern SIZE_PATTERN =
            Pattern.compile("^(\\d+)(b|kb|mb|gb)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Parses the given input string into a {@link Query}.
     *
     * @param input the raw user search input
     * @return a structured {@link Query} with type, value, and filters
     * @throws IllegalArgumentException if input is null or blank
     */
    public Query parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        String trimmed = input.trim();
        Map<String, String> filters = collapseFilters(extractFilters(trimmed));
        String remaining = extractRemaining(trimmed);
        QueryType type = detectType(remaining, filters);

        return new Query(type, remaining.isEmpty() ? null : remaining, Map.copyOf(filters));
    }

    /**
     * Extracts all {@code key:value} qualifiers from the input into a raw
     * multi-value map. Duplicate keys accumulate into a list.
     */
    private Map<String, List<String>> extractFilters(String input) {
        Map<String, List<String>> filters = new HashMap<>();
        Matcher matcher = METADATA_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            if (key.equalsIgnoreCase("size")) {
                value = normalizeSizeToBytes(value);
            }
            filters.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return filters;
    }

    /**
     * Strips all {@code key:value} qualifiers from the input and returns
     * the leftover free-text term, trimmed and whitespace-normalized.
     */
    private String extractRemaining(String input) {
        return METADATA_PATTERN.matcher(input)
                .replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Collapses each multi-value filter list into a single AND-joined string, to handle duplicate filters.
     */
    private Map<String, String> collapseFilters(Map<String, List<String>> filters) {
        Map<String, String> collapsed = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
            collapsed.put(entry.getKey(), String.join(" AND ", entry.getValue()));
        }
        return collapsed;
    }

    /**
     * Determines the {@link QueryType} based on whether filters and/or a
     * free-text term are present.
     */
    private QueryType detectType(String remaining, Map<String, String> filters) {
        if (filters.isEmpty()) {
            return FILENAME_PATTERN.matcher(remaining).matches() ? QueryType.FILENAME : QueryType.FULLTEXT;
        }
        return remaining.isEmpty() ? QueryType.METADATA : QueryType.MIXED;
    }

    /**
     * Normalizes a size string (e.g. {@code "10mb"}) to its byte equivalent
     * as a plain string. Defaults to bytes if no unit is provided.
     *
     * @throws IllegalArgumentException if the value is invalid or too large
     */
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