package app.search.query;

import java.util.Map;

/**
 * Parsed representation of a user search request.
 * <p>
 * {@code type} controls whether the query is treated as full-text, filename,
 * metadata-filter, or a combination.
 */
public record Query(
        QueryType type,
        String value,
        Map<String, String> filters
) {}
