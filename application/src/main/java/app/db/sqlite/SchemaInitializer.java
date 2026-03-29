package app.db.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates {@code files}, {@code files_fts} (FTS5), and {@code index_runs} if missing.
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
        }
    }
}

