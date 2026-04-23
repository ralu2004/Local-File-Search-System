package app.db.sqlite;

import app.repository.SearchActivityRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link app.repository.SearchActivityRepository}
 * for search logging, open-event tracking, and suggestions.
 */
public class SqliteSearchActivityRepository implements SearchActivityRepository {

    private final SqliteConnectionProvider connections;

    public SqliteSearchActivityRepository(SqliteConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public void recordSearch(String queryText, String normalizedQuery, int resultCount, long durationMs, String executedAt) throws SQLException {
        try (Connection conn = connections.open()) {
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO search_history (query_text, normalized_query, result_count, duration_ms, executed_at)\n" +
                    "VALUES (?, ?, ?, ?, ?)")) {
                stmt.setString(1, queryText);
                stmt.setString(2, normalizedQuery);
                stmt.setInt(3, resultCount);
                stmt.setLong(4, durationMs);
                stmt.setString(5, executedAt);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    public void recordResultOpen(String queryText, String normalizedQuery, String filePath, Integer resultPosition, String openedAt) throws SQLException {
        try (Connection conn = connections.open()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    """
                    INSERT INTO result_open_history (query_text, normalized_query, file_path, result_position, opened_at)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                stmt.setString(1, queryText);
                stmt.setString(2, normalizedQuery);
                stmt.setString(3, filePath);
                if (resultPosition == null) {
                    stmt.setNull(4, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(4, resultPosition);
                }
                stmt.setString(5, openedAt);
                stmt.executeUpdate();
            }
        }
    }

    @Override
    public List<String> suggestQueries(String normalizedPrefix, int limit) throws SQLException {
        String sql = """
                SELECT query_text
                FROM search_history
                WHERE normalized_query LIKE ? || '%'
                GROUP BY normalized_query, query_text
                ORDER BY COUNT(*) DESC, MAX(executed_at) DESC
                LIMIT ?
                """;
        List<String> suggestions = new ArrayList<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedPrefix);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    suggestions.add(rs.getString("query_text"));
                }
            }
        }
        return suggestions;
    }

    @Override
    public List<String> recentQueries(int limit) throws SQLException {
        String sql = """
                SELECT query_text
                FROM search_history
                GROUP BY normalized_query, query_text
                ORDER BY MAX(executed_at) DESC
                LIMIT ?
                """;
        List<String> recent = new ArrayList<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    recent.add(rs.getString("query_text"));
                }
            }
        }
        return recent;
    }
}
