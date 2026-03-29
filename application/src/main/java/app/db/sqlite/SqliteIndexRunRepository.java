package app.db.sqlite;

import app.indexer.IndexReport;
import app.model.IndexRun;
import app.repository.IndexRunRepository;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of {@link app.repository.IndexRunRepository} for
 * indexing job start/end and history listing.
 */
public final class SqliteIndexRunRepository implements IndexRunRepository {

    private final SqliteConnectionProvider connections;

    public SqliteIndexRunRepository(SqliteConnectionProvider connections) {
        this.connections = connections;
    }

    @Override
    public long startIndexing(LocalDateTime startedAt, String rootPath) throws SQLException {
        try (Connection conn = connections.open()) {
            String statement = "INSERT INTO index_runs (started_at, root_path) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(statement, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, startedAt.toString());
                stmt.setString(2, rootPath);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
            }
        }
        return 0;
    }

    @Override
    public void endIndexing(long runId, IndexReport report) throws SQLException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                String statement = """
                        UPDATE index_runs
                        SET finished_at=?, total_files=?, indexed=?, skipped=?, failed=?, deleted=?, elapsed_seconds=?
                        WHERE id=?
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(statement)) {
                    stmt.setString(1, LocalDateTime.now().toString());
                    stmt.setInt(2, report.totalFiles());
                    stmt.setInt(3, report.indexed());
                    stmt.setInt(4, report.skipped());
                    stmt.setInt(5, report.failed());
                    stmt.setInt(6, report.deleted());
                    stmt.setLong(7, report.elapsed().toSeconds());
                    stmt.setLong(8, runId);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<IndexRun> getHistory() throws SQLException {
        return queryIndexRuns("SELECT * FROM index_runs ORDER BY started_at DESC");
    }

    private List<IndexRun> queryIndexRuns(String sql, Object... params) throws SQLException {
        List<IndexRun> runs = new ArrayList<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(SqliteRowMappers.indexRun(rs));
                }
            }
        }
        return runs;
    }
}

