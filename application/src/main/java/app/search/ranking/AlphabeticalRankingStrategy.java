package app.search.ranking;

/**
 * Alphabetical ranking strategy — orders results by filename ascending,
 * using case-insensitive collation.
 */
public class AlphabeticalRankingStrategy implements RankingStrategy {

    @Override
    public String orderByClause() {
        return "f.filename COLLATE NOCASE ASC";
    }
}