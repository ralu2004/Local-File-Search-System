package app.search.ranking;

/**
 * Strategy for ordering search results.
 * <p>
 * Each implementation returns a SQL {@code ORDER BY} clause fragment that is
 * appended directly by {@link app.db.QueryBuilder}. Implementations that use
 * path features reference the {@code pf} alias (joined from {@code path_features});
 * those that sort by file metadata reference the {@code f} alias (from {@code files}).
 */
public interface RankingStrategy {

    /**
     * Returns the SQL {@code ORDER BY} clause for this strategy, without the
     * {@code ORDER BY} keyword itself.
     * <p>
     * Example: {@code "(0.3 * pf.depth + 0.3 * pf.extension_score) DESC"}
     *
     * @return a non-null SQL order expression
     */
    String orderByClause();

    /**
     * Whether this ranking strategy should emit user-facing ranking insights.
     * <p>
     * Defaults to {@code false} so non-behavior strategies keep the current payload
     * shape unless they explicitly opt in.
     */
    default boolean producesInsights() {
        return false;
    }
}