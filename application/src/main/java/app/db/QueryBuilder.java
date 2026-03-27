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
                        SELECT path,
                               filename,
                               COALESCE(
                                   snippet(files_fts, 2, '', '', '...', 24),
                                   files_fts.preview
                               ) AS preview,
                               rank
                        FROM files_fts
                        WHERE files_fts MATCH ?
                    )
                    SELECT m.path, m.filename, m.preview, f.extension, f.modified_at, f.size_bytes
                    FROM matched m
                    JOIN files f ON f.path = m.path
                    """);
            params.add(toFtsPrefixQuery(query.value()));
        } else {
            sql.append("""
                    SELECT fts.path, fts.filename, fts.preview, f.extension, f.modified_at, f.size_bytes
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

    private String toFtsPrefixQuery(String input) {
        if (input == null) return "";
        String query = input.trim();
        if (query.isEmpty()) return "";

        List<String> out = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '"') {
                if (token.length() > 0) {
                    out.add(transformToken(token.toString(), false));
                    token.setLength(0);
                }
                inQuotes = !inQuotes;
                out.add("\"");
                continue;
            }

            if (!inQuotes && Character.isWhitespace(c)) {
                if (token.length() > 0) {
                    out.add(transformToken(token.toString(), false));
                    token.setLength(0);
                }
                continue;
            }

            token.append(c);
        }

        if (token.length() > 0) {
            out.add(transformToken(token.toString(), inQuotes));
        }

        StringBuilder rebuilt = new StringBuilder();
        boolean openQuoteJustAppended = false;
        for (String part : out) {
            if (part.equals("\"")) {
                if (openQuoteJustAppended) {
                    rebuilt.append("\"");
                    openQuoteJustAppended = false;
                } else {
                    if (!rebuilt.isEmpty() && rebuilt.charAt(rebuilt.length() - 1) != ' ') rebuilt.append(' ');
                    rebuilt.append("\"");
                    openQuoteJustAppended = true;
                }
                continue;
            }

            if (!rebuilt.isEmpty() && rebuilt.charAt(rebuilt.length() - 1) != '"' && rebuilt.charAt(rebuilt.length() - 1) != ' ') {
                rebuilt.append(' ');
            }
            rebuilt.append(part);
        }

        return rebuilt.toString().trim();
    }

    private String transformToken(String rawToken, boolean inQuotes) {
        if (rawToken == null) return "";
        String token = rawToken.trim();
        if (token.isEmpty()) return "";

        if (inQuotes) {
            return token.replace("\"", "\"\"");
        }

        String upper = token.toUpperCase();
        if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT") || upper.equals("NEAR")) {
            return upper;
        }

        if (token.endsWith("*")) return token;

        if (token.matches("[A-Za-z0-9_]+")) {
            return token + "*";
        }

        return "\"" + token.replace("\"", "\"\"") + "\"";
    }
}