package app.db;

import java.util.List;

record BuiltQuery(String sql, List<Object> params) { }
