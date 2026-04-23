package app.repository;

import java.sql.SQLException;
import java.util.List;

/**
 * Persistence contract for search execution history, result-open tracking,
 * and query suggestions.
 */
public interface SearchActivityRepository {

    void recordSearch(String queryText, String normalizedQuery, int resultCount, long durationMs, String executedAt) throws SQLException;
    void recordResultOpen(String queryText, String normalizedQuery, String filePath, Integer resultPosition, String openedAt) throws SQLException;
    List<String> suggestQueries(String normalizedPrefix, int limit) throws SQLException;
    List<String> recentQueries(int limit) throws SQLException;
}