package app.search.ranking;

/**
 * Alphabetical ranking strategy — orders results by filename ascending.
 */
public class AlphabeticalRankingStrategy implements RankingStrategy {

    @Override
    public String orderByClause() {
        return "f.filename ASC";
    }
}