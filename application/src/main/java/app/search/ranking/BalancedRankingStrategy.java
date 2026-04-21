package app.search.ranking;

/**
 * Balanced ranking strategy — all four path feature signals weighted equally.
 * <p>
 * Unlike {@link StaticRankingStrategy}, no signal is favoured over another.
 */
public class BalancedRankingStrategy implements RankingStrategy {

    private static final double WEIGHT = 0.25;

    @Override
    public String orderByClause() {
        return "(" +
                WEIGHT + " * pf.depth" +
                " + " + WEIGHT + " * pf.extension_score" +
                " + " + WEIGHT + " * pf.directory_score" +
                " + " + WEIGHT + " * pf.filename_score" +
                ") DESC";
    }
}