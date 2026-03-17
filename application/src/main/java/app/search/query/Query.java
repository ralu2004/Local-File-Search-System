package app.search.query;

import java.util.Map;

public record Query(
        QueryType type,
        String value,
        Map<String, String> filters
) {}
