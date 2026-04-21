package app.db.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code files}, {@code files_fts} (FTS5), and {@code index_runs} if missing.
 * <p>
 * Runs lightweight migrations for existing databases — ensures
 * the {@code path_features} table exists for Iteration 2 ranking.
 */
public final class SchemaInitializer {

    private SchemaInitializer() {}

    public static void initialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
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
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_extension ON files(extension);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_modified_at ON files(modified_at);");
            stmt.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS files_fts USING fts5(
                        path        UNINDEXED,
                        filename,
                        content,
                        preview     UNINDEXED
                    );
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS index_runs (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at      TEXT,
                        finished_at     TEXT,
                        root_path       TEXT,
                        total_files     INTEGER,
                        indexed         INTEGER,
                        skipped         INTEGER,
                        failed          INTEGER,
                        deleted         INTEGER,
                        elapsed_seconds INTEGER
                    );
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS path_features (
                        path            TEXT PRIMARY KEY,
                        depth           REAL DEFAULT 0.0 CHECK(depth >= 0.0 AND depth <= 1.0),
                        extension_score REAL DEFAULT 0.0 CHECK(extension_score >= 0.0 AND extension_score <= 1.0),
                        directory_score REAL DEFAULT 0.0 CHECK(directory_score >= 0.0 AND directory_score <= 1.0),
                        filename_score  REAL DEFAULT 0.0 CHECK(filename_score >= 0.0 AND filename_score <= 1.0),
                        FOREIGN KEY (path) REFERENCES files(path) ON DELETE CASCADE
                    );
                """);
        }
    }
}