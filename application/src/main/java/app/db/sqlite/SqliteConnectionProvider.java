package app.db.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sqlite.Function;

/**
 * Opens JDBC connections to SQLite and applies common PRAGMA settings
 * (busy timeout, WAL, synchronous).
 */
public final class SqliteConnectionProvider {

    private static final Logger log = LoggerFactory.getLogger(SqliteConnectionProvider.class);
    private static final int SQLITE_BUSY_TIMEOUT_MS = 5_000;
    private static final String SQLITE_JOURNAL_MODE = "WAL";
    private static final String SQLITE_SYNCHRONOUS = "NORMAL";

    private static volatile boolean udfVerified = false;
    private final String jdbcUrl;

    public SqliteConnectionProvider(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public Connection open() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout = " + SQLITE_BUSY_TIMEOUT_MS + ";");
            stmt.execute("PRAGMA journal_mode = " + SQLITE_JOURNAL_MODE + ";");
            stmt.execute("PRAGMA synchronous = " + SQLITE_SYNCHRONOUS + ";");
            stmt.execute("PRAGMA foreign_keys = ON;");
        } catch (SQLException e) {
            log.warn("Failed to apply SQLite PRAGMAs: {}", e.getMessage());
        }

        registerFunctions(conn);

        return conn;
    }

    private void registerFunctions(Connection conn) throws SQLException {
        Function.create(conn, SqliteBehaviorScoreFunction.NAME, new SqliteBehaviorScoreFunction(), SqliteBehaviorScoreFunction.ARG_COUNT);
        if (!udfVerified) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT " + SqliteBehaviorScoreFunction.NAME + "(0, NULL, 0, 0, 0)");
                udfVerified = true;
            }
        }
    }
}

