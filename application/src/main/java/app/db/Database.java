package app.db;

import app.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Database implements AutoCloseable {

    private static final String DB_PATH = System.getProperty("user.dir") + "/.searchengine/index.db";
    private Connection connection;

    public Database() throws SQLException, IOException {
        Path path = Paths.get(DB_PATH);
        Files.createDirectories(path.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        connection.setAutoCommit(true);
        initializeSchema();
        System.out.println("Database path: " + DB_PATH);
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS files (
                        path        TEXT PRIMARY KEY,
                        filename    TEXT NOT NULL,
                        extension   TEXT,
                        size_bytes  INTEGER,
                        created_at  TEXT,
                        modified_at TEXT,
                        indexed_at  TEXT
                    );
                    """);
            stmt.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS files_fts USING fts5(
                        path        UNINDEXED,
                        filename,
                        content,
                        preview     UNINDEXED,
                    );
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS index_runs (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at  TEXT,
                        finished_at TEXT,
                        total_files INTEGER,
                        indexed     INTEGER,
                        skipped     INTEGER,
                        failed      INTEGER
                    );
                    """);
        }
    }

    public void upsert(FileRecord record, String content, String preview) throws SQLException {
        String statement = """
            INSERT OR REPLACE INTO files (path, filename, extension, size_bytes, created_at, modified_at, indexed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, record.path().toString());
            stmt.setString(2, record.filename());
            stmt.setString(3, record.extension());
            stmt.setLong(4, record.sizeBytes());
            stmt.setString(5, record.createdAt().toString());
            stmt.setString(6, record.modifiedAt().toString());
            stmt.setString(7, LocalDateTime.now().toString());
            stmt.executeUpdate();
        }

        statement = "DELETE FROM files_fts WHERE path = ?";
        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, record.path().toString());
            stmt.executeUpdate();
        }

        statement = """
            INSERT OR REPLACE INTO files_fts (path, filename, content, preview)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, record.path().toString());
            stmt.setString(2, record.filename());
            stmt.setString(3, content);
            stmt.setString(4, preview);
            stmt.executeUpdate();
        }
    }

    public void delete(Path path) throws SQLException {
        String statement = """
                DELETE FROM files
                WHERE path = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, path.toString());
            stmt.executeUpdate();
        }

        statement = """
                DELETE FROM files_fts
                WHERE path = ?
                """;

        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, path.toString());
            stmt.executeUpdate();
        }
    }

    public List<SearchResult> search(String query) throws SQLException {
        String statement = """
            WITH matched AS (
            SELECT path, filename, preview, rank
            FROM files_fts
            WHERE files_fts MATCH ?
            )
            SELECT m.path, m.filename, m.preview, f.extension, f.modified_at
            FROM matched m
            JOIN files f ON f.path = m.path
            ORDER BY m.rank
            """;
        List<SearchResult> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, query);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchResult(
                            Path.of(rs.getString("path")),
                            rs.getString("filename"),
                            rs.getString("extension"),
                            rs.getString("preview"),
                            LocalDateTime.parse(rs.getString("modified_at"))
                    ));
                }
            }
        }
        return results;
    }

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}