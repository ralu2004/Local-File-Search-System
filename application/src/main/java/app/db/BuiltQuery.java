package app.db;

import java.util.List;

/**
 * SQL text and ordered bind parameters produced by {@link QueryBuilder}.
 */
public record BuiltQuery(String sql, List<Object> params) { }
