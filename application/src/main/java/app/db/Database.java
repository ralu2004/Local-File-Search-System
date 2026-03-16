package app.db;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;

public class Database implements AutoCloseable {

    private static final String DB_PATH = System.getProperty("user.dir") + "/.searchengine/index.db";
    private Connection connection;

    public Database() throws SQLException, IOException {
        Path path = Paths.get(DB_PATH);
        Files.createDirectories(path.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        initializeSchema();
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
                        content='files',
                        content_rowid='rowid'
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

    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}