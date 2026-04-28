package app.db;

import app.db.sqlite.SchemaInitializer;
import app.db.sqlite.SqliteConnectionProvider;
import app.db.sqlite.SqliteDatabaseSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Default provider that creates SQLite-backed {@link DatabaseSession} instances.
 * Owns schema initialization: applies the schema to the underlying SQLite file
 * before constructing and returning the session.
 */
public final class SqliteDatabaseProvider implements DatabaseProvider {

    private static final String DEFAULT_PATH = System.getProperty("user.dir") + "/.searchengine/index.db";

    @Override
    public DatabaseSession openDefault() throws SQLException, IOException {
        return open(DEFAULT_PATH);
    }

    @Override
    public DatabaseSession open(String dbPath) throws SQLException, IOException {
        Path path = Paths.get(dbPath);
        Files.createDirectories(path.getParent());
        SqliteConnectionProvider connections = new SqliteConnectionProvider("jdbc:sqlite:" + dbPath);
        initializeSchema(connections);
        return new SqliteDatabaseSession(connections);
    }

    private void initializeSchema(SqliteConnectionProvider connections) throws SQLException {
        try (Connection conn = connections.open()) {
            SchemaInitializer.initialize(conn);
        }
    }
}
