package app.service.support;

import app.db.Database;
import app.db.DatabaseProvider;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Shared helper for opening the configured database path or default database.
 */
public final class DatabaseAccessor {

    private final DatabaseProvider databaseProvider;

    public DatabaseAccessor(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    public Database openDatabase(String dbPath) throws SQLException, IOException {
        if (dbPath == null || dbPath.isBlank()) {
            return databaseProvider.openDefault();
        }
        return databaseProvider.open(dbPath);
    }
}
