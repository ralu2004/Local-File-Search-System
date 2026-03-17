package app.search.query;

public record Query(
        QueryType type,
        String value,
        String field
) {}
