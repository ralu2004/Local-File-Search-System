package app.repository;

import app.model.RankedSearchResult;
import app.search.query.Query;
import app.search.ranking.RankingStrategy;

import java.sql.SQLException;
import java.util.List;

/**
 * Read-only search contract over indexed file data.
 */
public interface FileSearchRepository {
    List<RankedSearchResult> search(Query query, int limit, RankingStrategy strategy, String normalizedQuery) throws SQLException;
}
