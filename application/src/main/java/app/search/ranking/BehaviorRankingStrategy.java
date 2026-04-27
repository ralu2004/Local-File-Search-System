package app.search.ranking;

import app.db.sqlite.SqliteBehaviorScoreFunction;

/**
 * Personalized ranking strategy that orders results using the user's
 * interaction history with the current normalized query.
 * <p>
 * Implements a <b>two-bucket model</b>:
 * <ol>
 *   <li><b>Bucket A</b> — files with any open history for this query — sort first,
 *       ranked among themselves by the {@code behavior_score} UDF.</li>
 *   <li><b>Bucket B</b> — files with no open history — sort after, falling through
 *       to whatever tie-breakers {@link app.db.QueryBuilder} appends (FTS rank for
 *       FTS queries, the limit clause otherwise).</li>
 * </ol>
 */
public class BehaviorRankingStrategy implements RankingStrategy {

    @Override
    public String orderByClause() {
        return """
                CASE WHEN COALESCE(roh.open_count, 0) > 0 THEN 1 ELSE 0 END DESC, \
                %s(\
                COALESCE(roh.open_count, 0), \
                roh.last_opened_at, \
                COALESCE(roh.position_sum, 0), \
                COALESCE(roh.position_count, 0), \
                COALESCE(sh.query_count, 0)\
                ) DESC""".formatted(SqliteBehaviorScoreFunction.NAME);
    }

    @Override
    public boolean producesInsights() {
        return true;
    }
}