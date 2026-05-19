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
 * <p>
 * Supports two ranking layers:
 * <ul>
 *   <li>Base relevance/order: FTS rank (when applicable) + configured {@link RankingStrategy}</li>
 *   <li>History boosts (when normalized query is provided): open frequency/recency and query frequency/recency</li>
 * </ul>
 * History boosts are sourced from {@code result_open_history} and {@code search_history}
 * to promote results users open more often for the same normalized query.
 */
public class QueryBuilder {

    /**
     * Tokenized piece used while rebuilding FTS expressions:
     * either a text token or a quote marker, plus quote-context metadata.
     */
    private record Part(String text, boolean isQuoteMarker, boolean inQuotes) {}

    private static final RankingStrategy DEFAULT_STRATEGY = new StaticRankingStrategy();

    private final RankingStrategy rankingStrategy;

    public QueryBuilder() {
        this(DEFAULT_STRATEGY);
    }

    public QueryBuilder(RankingStrategy rankingStrategy) {
        this.rankingStrategy = rankingStrategy;
    }

    /**
     * Builds a query with optional history signals.
     *
     * @param query user query object
     * @param limit max number of rows to return
     * @param normalizedQuery normalized history key; blank disables history joins/boosts
     */
    public BuiltQuery build(Query query, int limit, String normalizedQuery) {
        return buildInternal(query, limit, normalizedQuery == null ? "" : normalizedQuery.trim().toLowerCase());
    }

    public BuiltQuery build(Query query, int limit) {
        return buildInternal(query, limit, "");
    }

    /**
     * Main assembly pipeline:
     * select + optional history joins, filter predicates, ordering, then limit.
     */
    private BuiltQuery buildInternal(Query query, int limit, String normalizedQuery) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        boolean usesFts = hasFtsTerm(query) || query.filters().containsKey("content");
        boolean hasHistorySignal = !normalizedQuery.isBlank();
        boolean prioritizeStrategy = hasExplicitSort(query);

        appendBaseSelect(sql, params, query, hasHistorySignal);
        if (hasHistorySignal) {
            appendHistorySignalParams(params, normalizedQuery);
        }
        appendFilters(sql, query.filters(), params);
        appendOrdering(sql, usesFts, hasHistorySignal, prioritizeStrategy);
        appendLimit(sql, params, limit);
        return new BuiltQuery(sql.toString(), params);
    }

    private boolean hasExplicitSort(Query query) {
        String sort = query.filters().get("sort");
        return sort != null && !sort.isBlank();
    }

    private boolean hasFtsTerm(Query query) {
        return query.value() != null && !query.value().isBlank();
    }

    /**
     * Chooses between FTS-backed SELECT and plain SELECT. FTS path is used when
     * free text or {@code content:} requires MATCH syntax.
     */
    private void appendBaseSelect(StringBuilder sql, List<Object> params, Query query, boolean hasHistorySignal) {
        String ftsMatch = buildFtsMatchString(query);
        if (ftsMatch != null) {
            appendFtsSelect(sql, hasHistorySignal);
            params.add(ftsMatch);
            return;
        }
        appendPlainSelect(sql, hasHistorySignal);
    }

    /**
     * Builds the FTS MATCH expression from free text and optional content filter.
     * The free-text value is used as-is since FTS normalization and prefix
     * expansion are applied upstream by {@link app.search.query.preprocessor.LogicDecorator}.
     * Returns null when no FTS term is needed.
     */
    private String buildFtsMatchString(Query query) {
        String freeTerm = hasFtsTerm(query) ? query.value() : null;
        String contentTerm = query.filters().containsKey("content")
                ? "content:" + query.filters().get("content") : null;
        if (freeTerm != null && contentTerm != null) {
            return freeTerm + " AND " + contentTerm;
        }
        if (freeTerm != null) {
            return freeTerm;
        }
        return contentTerm;
    }

    /**
     * Appends the FTS-based SELECT with optional history joins and projected
     * behavior signal columns used by ranking insights.
     */
    private void appendFtsSelect(StringBuilder sql, boolean hasHistorySignal) {
        String signalColumns = getSignalColumns(hasHistorySignal);
        sql.append(
                "WITH matched AS (\n" +
                "    SELECT path,\n" +
                "           filename,\n" +
                "           COALESCE(\n" +
                "               snippet(files_fts, 2, '', '', '...', 24),\n" +
                "               files_fts.preview\n" +
                "           ) AS preview,\n" +
                "           rank\n" +
                "    FROM files_fts\n" +
                "    WHERE files_fts MATCH ?\n" +
                ")\n" +
                "SELECT m.path, m.filename, m.preview, f.extension, f.modified_at, f.size_bytes" +
                signalColumns +
                "\nFROM matched m\n" +
                "JOIN files f ON f.path = m.path\n" +
                "LEFT JOIN path_features pf ON pf.path = f.path\n"
        );
        if (hasHistorySignal) {
            appendHistoryBoostJoins(sql);
        }
    }

    /**
     * Appends the non-FTS SELECT path used for filter-only queries, while still
     * projecting behavior signal columns for stable result mapping.
     */
    private void appendPlainSelect(StringBuilder sql, boolean hasHistorySignal) {
        String signalColumns = getSignalColumns(hasHistorySignal);
        sql.append(
                "SELECT fts.path, fts.filename, fts.preview, f.extension, f.modified_at, f.size_bytes" +
                signalColumns +
                "\nFROM files f\n" +
                "JOIN files_fts fts ON fts.path = f.path\n" +
                "LEFT JOIN path_features pf ON pf.path = f.path\n"
        );
        if (hasHistorySignal) {
            appendHistoryBoostJoins(sql);
        }
    }

    /**
     * Projects behavior signal columns with either real history values or
     * stable zero/null defaults when history joins are disabled.
     */
    private static String getSignalColumns(boolean hasHistorySignal) {
        return hasHistorySignal
                ? ", COALESCE(roh.open_count, 0) AS open_count"
                  + ", roh.last_opened_at AS last_opened_at"
                  + ", COALESCE(roh.position_sum, 0) AS position_sum"
                  + ", COALESCE(roh.position_count, 0) AS position_count"
                : ", 0 AS open_count, NULL AS last_opened_at, 0 AS position_sum, 0 AS position_count";
    }

    /**
     * Joins per-query aggregates:
     * - {@code roh}: open-event frequency/recency/position signals by file
     * - {@code sh}: query frequency/recency signals for the normalized query
     */
    private void appendHistoryBoostJoins(StringBuilder sql) {
        sql.append("""
                LEFT JOIN (
                    SELECT file_path,
                           COUNT(*) AS open_count,
                           MAX(opened_at) AS last_opened_at,
                           SUM(COALESCE(result_position, 1)) AS position_sum,
                           SUM(CASE WHEN result_position IS NOT NULL THEN 1 ELSE 0 END) AS position_count
                    FROM result_open_history
                    WHERE normalized_query = ?
                    GROUP BY file_path
                ) roh ON roh.file_path = f.path
                LEFT JOIN (
                    SELECT normalized_query,
                           COUNT(*) AS query_count,
                           MAX(executed_at) AS last_query_at
                    FROM search_history
                    WHERE normalized_query = ?
                    GROUP BY normalized_query
                ) sh ON sh.normalized_query = ?
                """);
    }

    /**
     * Binds normalized query for all history subquery placeholders.
     */
    private void appendHistorySignalParams(List<Object> params, String normalizedQuery) {
        params.add(normalizedQuery);
        params.add(normalizedQuery);
        params.add(normalizedQuery);
    }

    /**
     * Ordering rules:
     * - FTS queries: default to MATCH rank, unless explicit sort is present
     * - explicit sort: strategy takes precedence; FTS rank remains a tie-breaker
     * - non-FTS queries: strategy ordering is primary
     * - history signals are injected as additional tie-breakers when enabled
     */
    private void appendOrdering(StringBuilder sql, boolean usesFts, boolean hasHistorySignal, boolean prioritizeStrategy) {
        if (usesFts) {
            sql.append(" ORDER BY ");
            if (prioritizeStrategy) {
                sql.append(rankingStrategy.orderByClause());
                if (hasHistorySignal) {
                    sql.append(", COALESCE(roh.open_count, 0) DESC")
                            .append(", COALESCE(roh.last_opened_at, '') DESC")
                            .append(", COALESCE(sh.query_count, 0) DESC")
                            .append(", COALESCE(sh.last_query_at, '') DESC");
                }
                sql.append(", m.rank");
            } else {
                sql.append("m.rank");
                if (hasHistorySignal) {
                    sql.append(", COALESCE(roh.open_count, 0) DESC")
                            .append(", COALESCE(roh.last_opened_at, '') DESC")
                            .append(", COALESCE(sh.query_count, 0) DESC")
                            .append(", COALESCE(sh.last_query_at, '') DESC");
                }
                sql.append(", ").append(rankingStrategy.orderByClause());
            }
        } else {
            sql.append(" ORDER BY ");
            if (hasHistorySignal) {
                sql.append("COALESCE(roh.open_count, 0) DESC")
                        .append(", COALESCE(roh.last_opened_at, '') DESC")
                        .append(", COALESCE(sh.query_count, 0) DESC")
                        .append(", COALESCE(sh.last_query_at, '') DESC")
                        .append(", ");
            }
            sql.append(rankingStrategy.orderByClause());
        }
    }

    /**
     * Appends hard row cap and binds the limit value.
     */
    private void appendLimit(StringBuilder sql, List<Object> params, int limit) {
        sql.append(" LIMIT ?");
        params.add(limit);
    }

    /**
     * Appends metadata filters outside FTS MATCH; each helper adds SQL predicate
     * fragments and associated parameters.
     */
    private void appendFilters(StringBuilder sql, Map<String, String> filters, List<Object> params) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        List<String> conditions = new ArrayList<>();
        addExtensionFilter(filters, conditions, params);
        addModifiedFilter(filters, conditions, params);
        addSizeFilter(filters, conditions, params);
        addPathFilter(filters, conditions, params);
        addColorFilter(filters, conditions, params);

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

    /**
     * Adds path predicates using slash-normalized matching so path filters work
     * across Windows and Unix separators.
     */
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
            conditions.add("REPLACE(f.path, char(92), '/') LIKE ?");
            params.add("%" + value.replace('\\', '/') + "%");
        }
    }

    private void addColorFilter(Map<String, String> filters, List<String> conditions, List<Object> params) {
        if (!filters.containsKey("color")) {
            return;
        }
        conditions.add("EXISTS (SELECT 1 FROM image_features imf WHERE imf.path = f.path AND imf.dominant_color = ?)");
        params.add(filters.get("color"));
    }
}
