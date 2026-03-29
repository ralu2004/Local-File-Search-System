package app.db.sqlite;

import app.model.FileRecord;
import app.model.IndexRun;
import app.model.SearchResult;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Maps JDBC {@link java.sql.ResultSet} rows to domain models for SQLite repositories.
 */
final class SqliteRowMappers {

    private SqliteRowMappers() {}

    static FileRecord fileRecord(ResultSet rs) throws SQLException {
        return new FileRecord(
                Path.of(rs.getString("path")),
                rs.getString("filename"),
                rs.getString("extension"),
                rs.getLong("size_bytes"),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("modified_at"))
        );
    }

    static SearchResult searchResult(ResultSet rs) throws SQLException {
        return new SearchResult(
                Path.of(rs.getString("path")),
                rs.getString("filename"),
                rs.getString("extension"),
                rs.getString("preview"),
                LocalDateTime.parse(rs.getString("modified_at")),
                rs.getObject("size_bytes", Long.class)
        );
    }

    static IndexRun indexRun(ResultSet rs) throws SQLException {
        LocalDateTime startedAt = LocalDateTime.parse(rs.getString("started_at"));
        String finishedAtStr = rs.getString("finished_at");
        LocalDateTime finishedAt = finishedAtStr != null ? LocalDateTime.parse(finishedAtStr) : null;
        Duration elapsed = Duration.ofSeconds(rs.getLong("elapsed_seconds"));

        return new IndexRun(
                rs.getLong("id"),
                startedAt,
                finishedAt,
                rs.getString("root_path"),
                rs.getInt("total_files"),
                rs.getInt("indexed"),
                rs.getInt("skipped"),
                rs.getInt("failed"),
                rs.getInt("deleted"),
                elapsed
        );
    }
}

