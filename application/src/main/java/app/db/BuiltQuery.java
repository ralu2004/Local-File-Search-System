package app.db;

import java.util.List;

public record BuiltQuery(String sql, List<Object> params) { }
