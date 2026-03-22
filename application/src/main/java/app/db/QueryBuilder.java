package app.db;

import app.search.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class QueryBuilder {

    BuiltQuery build(Query query, int limit) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        boolean hasFTS = query.value() != null && !query.value().isBlank();
        boolean hasFilters = query.filters() != null && !query.filters().isEmpty();

        if (hasFTS) {
            sql.append("""
                    WITH matched AS (
                        SELECT path, filename, preview, rank
                        FROM files_fts
                        WHERE files_fts MATCH ?
                    )
                    SELECT m.path, m.filename, m.preview, f.extension, f.modified_at
                    FROM matched m
                    JOIN files f ON f.path = m.path
                    """);
            params.add(sanitizeQuery(query.value()));
        } else {
            sql.append("""
                    SELECT fts.path, fts.filename, fts.preview, f.extension, f.modified_at
                    FROM files f
                    JOIN files_fts fts ON fts.path = f.path
                    """);
        }

        if (hasFilters) {
            appendFilters(sql, query.filters(), params);
        }

        if (hasFTS) {
            sql.append(" ORDER BY m.rank");
        }

        sql.append(" LIMIT ?");
        params.add(limit);
        return new BuiltQuery(sql.toString(), params);
    }

    private void appendFilters(StringBuilder sql, Map<String, String> filters, List<Object> params) {
        List<String> conditions = new ArrayList<>();

        if (filters.containsKey("ext")) {
            conditions.add("f.extension = ?");
            params.add(filters.get("ext"));
        }
        if (filters.containsKey("modified")) {
            conditions.add("f.modified_at > ?");
            params.add(filters.get("modified"));
        }
        if (filters.containsKey("size")) {
            conditions.add("f.size_bytes > ?");
            params.add(Long.parseLong(filters.get("size")));
        }

        if (!conditions.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", conditions));
        }
    }

    private String sanitizeQuery(String query) {
        if (query.matches(".*[.\\-+*()^\":].*")) {
            return "\"" + query.replace("\"", "\"\"") + "\"";
        }
        return query;
    }
}