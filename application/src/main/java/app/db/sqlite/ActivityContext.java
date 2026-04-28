package app.db.sqlite;

import app.repository.CloseableSearchActivity;

import java.sql.SQLException;
import java.util.List;

/**
 * Persistence context for search execution and result-open activity.
 * Implements the {@link CloseableSearchActivity} view and delegates to
 * {@link SqliteSearchActivityRepository}. Created and owned by
 * {@link SqliteDatabaseSession}.
 */
public final class ActivityContext implements CloseableSearchActivity {

    private final SqliteSearchActivityRepository searchActivityRepository;

    ActivityContext(SqliteConnectionProvider connections) {
        this.searchActivityRepository = new SqliteSearchActivityRepository(connections);
    }

    @Override
    public void recordSearch(String queryText, String normalizedQuery, int resultCount, long durationMs, String executedAt) throws SQLException {
        searchActivityRepository.recordSearch(queryText, normalizedQuery, resultCount, durationMs, executedAt);
    }

    @Override
    public void recordResultOpen(String queryText, String normalizedQuery, String filePath, Integer resultPosition, String openedAt) throws SQLException {
        searchActivityRepository.recordResultOpen(queryText, normalizedQuery, filePath, resultPosition, openedAt);
    }

    @Override
    public List<String> suggestQueries(String normalizedPrefix, int limit) throws SQLException {
        return searchActivityRepository.suggestQueries(normalizedPrefix, limit);
    }

    @Override
    public List<String> recentQueries(int limit) throws SQLException {
        return searchActivityRepository.recentQueries(limit);
    }

    @Override
    public void close() throws SQLException {
        // there are no long-lived resources; connections are per-method via SqliteConnectionProvider.
    }
}
