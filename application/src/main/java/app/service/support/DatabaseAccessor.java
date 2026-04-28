package app.service.support;

import app.db.Database;
import app.db.DatabaseProvider;
import app.repository.CloseableFileMetadata;
import app.repository.CloseableFileSearch;
import app.repository.CloseableFileWrite;
import app.repository.CloseableIndexRuns;
import app.repository.CloseableSearchActivity;

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

    public CloseableFileSearch openFileSearch(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public CloseableSearchActivity openSearchActivity(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public CloseableIndexRuns openIndexRuns(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public CloseableFileWrite openFileWrite(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public CloseableFileMetadata openFileMetadata(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }
}
