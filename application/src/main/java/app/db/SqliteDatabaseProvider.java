package app.db;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Default provider that creates SQLite-backed {@link Database} instances.
 */
public final class SqliteDatabaseProvider implements DatabaseProvider {
    @Override
    public Database openDefault() throws SQLException, IOException {
        return new Database();
    }

    @Override
    public Database open(String dbPath) throws SQLException, IOException {
        return new Database(dbPath);
    }
}
