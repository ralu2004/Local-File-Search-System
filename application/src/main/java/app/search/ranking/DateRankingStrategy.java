package app.search.ranking;

/**
 * Date ranking strategy — orders results by last modified date, newest first.
 */
public class DateRankingStrategy implements RankingStrategy {

    @Override
    public String orderByClause() {
        return "f.modified_at DESC";
    }
}