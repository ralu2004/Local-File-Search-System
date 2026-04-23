package app.db;

import app.search.query.Query;
import app.search.ranking.RankingStrategy;
import app.search.ranking.StaticRankingStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds search SQL queries for SQLite FTS5 ({@code files_fts}), as well as
 * optional filters on {@code files} (extension, modified time, size, path, content).
 * Normalizes user input into a valid FTS5 {@code MATCH} query string.
 */
public class QueryBuilder {
    
    private record Part(String text, boolean isQuoteMarker, boolean inQuotes) {}

    private static final RankingStrategy DEFAULT_STRATEGY = new StaticRankingStrategy();

    private final RankingStrategy rankingStrategy;

    public QueryBuilder() {
        this(DEFAULT_STRATEGY);
    }

    public QueryBuilder(RankingStrategy rankingStrategy) {
        this.rankingStrategy = rankingStrategy;
    }

    public BuiltQuery build(Query query, int limit) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        boolean usesFts = hasFtsTerm(query) || query.filters().containsKey("content");

        appendBaseSelect(sql, params, query);
        appendFilters(sql, query.filters(), params);
        appendOrdering(sql, usesFts);
        appendLimit(sql, params, limit);
        return new BuiltQuery(sql.toString(), params);
    }

    private boolean hasFtsTerm(Query query) {
        return query.value() != null && !query.value().isBlank();
    }

    private void appendBaseSelect(StringBuilder sql, List<Object> params, Query query) {
        String ftsMatch = buildFtsMatchString(query);
        if (ftsMatch != null) {
            appendFtsSelect(sql);
            params.add(ftsMatch);
            return;
        }
        appendPlainSelect(sql);
    }

    private String buildFtsMatchString(Query query) {
        String freeTerm = hasFtsTerm(query) ? toFtsPrefixQuery(query.value()) : null;
        String contentTerm = query.filters().containsKey("content") ? "content:" + query.filters().get("content") : null;

        if (freeTerm != null && contentTerm != null) {
            return freeTerm + " AND " + contentTerm;
        }

        if (freeTerm != null) return freeTerm;

        if (contentTerm != null) return contentTerm;

        return null;
    }

    private void appendFtsSelect(StringBuilder sql) {
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
                LEFT JOIN path_features pf ON pf.path = f.path
                """);
    }

    private void appendPlainSelect(StringBuilder sql) {
        sql.append("""
                SELECT fts.path, fts.filename, fts.preview, f.extension, f.modified_at, f.size_bytes
                FROM files f
                JOIN files_fts fts ON fts.path = f.path
                LEFT JOIN path_features pf ON pf.path = f.path
                """);
    }

    private void appendOrdering(StringBuilder sql, boolean usesFts) {
        if (usesFts) {
            sql.append(" ORDER BY m.rank, ").append(rankingStrategy.orderByClause());
        } else {
            sql.append(" ORDER BY ").append(rankingStrategy.orderByClause());
        }
    }

    private void appendLimit(StringBuilder sql, List<Object> params, int limit) {
        sql.append(" LIMIT ?");
        params.add(limit);
    }

    private void appendFilters(StringBuilder sql, Map<String, String> filters, List<Object> params) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        List<String> conditions = new ArrayList<>();
        addExtensionFilter(filters, conditions, params);
        addModifiedFilter(filters, conditions, params);
        addSizeFilter(filters, conditions, params);
        addPathFilter(filters, conditions, params);

        if (!conditions.isEmpty()) {
            sql.append("WHERE ").append(String.join(" AND ", conditions));
        }
    }

    private void addExtensionFilter(Map<String, String> filters, List<String> conditions, List<Object> params) {
        if (!filters.containsKey("ext")) {
            return;
        }
        conditions.add("f.extension = ?");
        params.add(filters.get("ext"));
    }

    private void addModifiedFilter(Map<String, String> filters, List<String> conditions, List<Object> params) {
        if (!filters.containsKey("modified")) {
            return;
        }
        conditions.add("f.modified_at > ?");
        params.add(filters.get("modified"));
    }

    private void addSizeFilter(Map<String, String> filters, List<String> conditions, List<Object> params) {
        if (!filters.containsKey("size")) {
            return;
        }
        conditions.add("f.size_bytes > ?");
        params.add(Long.parseLong(filters.get("size")));
    }

    private void addPathFilter(Map<String, String> filters, List<String> conditions, List<Object> params) {
        if (!filters.containsKey("path")) {
            return;
        }
        String rawPathFilter = filters.get("path");
        if (rawPathFilter == null || rawPathFilter.isBlank()) {
            return;
        }

        String[] segments = rawPathFilter.split("\\s+AND\\s+");
        for (String segment : segments) {
            String value = segment.trim();
            if (value.isEmpty()) {
                continue;
            }
            // normalize stored Windows paths (\) to Unix-style (/) so path:src/main works cross-platform.
            conditions.add("REPLACE(f.path, char(92), '/') LIKE ?");
            params.add("%" + value.replace('\\', '/') + "%");
        }
    }

    private String toFtsPrefixQuery(String input) {
        if (input == null) return "";
        String query = input.trim();
        if (query.isEmpty()) return "";

        List<Part> parts = splitIntoParts(query);
        int lastPrefixCandidateIndex = findLastPrefixCandidateIndex(parts);
        return rebuildFtsQuery(parts, lastPrefixCandidateIndex);
    }

    private List<Part> splitIntoParts(String query) {
        List<Part> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '"') {
                flushToken(parts, token, false);
                inQuotes = !inQuotes;
                parts.add(new Part("\"", true, false));
                continue;
            }
            if (!inQuotes && Character.isWhitespace(c)) {
                flushToken(parts, token, false);
                continue;
            }
            token.append(c);
        }

        flushToken(parts, token, inQuotes);
        return parts;
    }

    private void flushToken(List<Part> parts, StringBuilder token, boolean inQuotes) {
        if (token.length() == 0) {
            return;
        }
        parts.add(new Part(token.toString(), false, inQuotes));
        token.setLength(0);
    }

    private int findLastPrefixCandidateIndex(List<Part> parts) {
        int index = -1;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.isQuoteMarker || part.inQuotes) continue;
            if (isPrefixCandidate(part.text)) {
                index = i;
            }
        }
        return index;
    }

    private String rebuildFtsQuery(List<Part> parts, int lastPrefixCandidateIndex) {
        StringBuilder rebuilt = new StringBuilder();
        boolean openQuoteJustAppended = false;

        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.isQuoteMarker) {
                openQuoteJustAppended = appendQuoteMarker(rebuilt, openQuoteJustAppended);
                continue;
            }

            String transformed = transformToken(part.text, part.inQuotes, i == lastPrefixCandidateIndex);
            if (shouldInsertTokenSeparator(rebuilt)) {
                rebuilt.append(' ');
            }
            rebuilt.append(transformed);
        }
        return rebuilt.toString().trim();
    }

    private boolean appendQuoteMarker(StringBuilder rebuilt, boolean openQuoteJustAppended) {
        if (openQuoteJustAppended) {
            rebuilt.append("\"");
            return false;
        }
        if (!rebuilt.isEmpty() && rebuilt.charAt(rebuilt.length() - 1) != ' ') {
            rebuilt.append(' ');
        }
        rebuilt.append("\"");
        return true;
    }

    private boolean shouldInsertTokenSeparator(StringBuilder rebuilt) {
        if (rebuilt.isEmpty()) {
            return false;
        }
        char last = rebuilt.charAt(rebuilt.length() - 1);
        return last != '"' && last != ' ';
    }

    private boolean isPrefixCandidate(String rawToken) {
        if (rawToken == null) return false;
        String token = rawToken.trim();
        if (token.isEmpty()) return false;
        if (token.endsWith("*")) return false;
        String upper = token.toUpperCase();
        if (upper.equals("AND") || upper.equals("OR") || upper.equals("NOT") || upper.equals("NEAR")) return false;
        return token.matches("[A-Za-z0-9_]+");
    }

    private String transformToken(String rawToken, boolean inQuotes, boolean shouldPrefix) {
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
            return shouldPrefix ? token + "*" : token;
        }

        return "\"" + token.replace("\"", "\"\"") + "\"";
    }
}