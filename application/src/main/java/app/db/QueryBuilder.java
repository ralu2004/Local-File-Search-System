package app.db;

import app.search.query.Query;

import java.util.ArrayList;
import java.util.List;

class QueryBuilder {

    private String sanitizeQuery(String query) {
        if (query.matches(".*[.\\-+*()^\":].*")) {
            return "\"" + query.replace("\"", "\"\"") + "\"";
        }
        return query;
    }

    BuiltQuery build(Query query) {
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
            sql.append("WHERE ");
            List<String> conditions = new ArrayList<>();

            if (query.filters().containsKey("ext")) {
                conditions.add("f.extension = ?");
                params.add(query.filters().get("ext"));
            }
            if (query.filters().containsKey("modified")) {
                conditions.add("f.modified_at > ?");
                params.add(query.filters().get("modified"));
            }
            if (query.filters().containsKey("size")) {
                conditions.add("f.size_bytes > ?");
                params.add(Long.parseLong(query.filters().get("size")));
            }

            sql.append(String.join(" AND ", conditions));
        }

        if (hasFTS) {
            sql.append(" ORDER BY m.rank");
        }
        return new BuiltQuery(sql.toString(), params);
    }

}