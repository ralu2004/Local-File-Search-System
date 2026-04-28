package app.service.support;

import app.db.Database;
import app.db.DatabaseProvider;
import app.repository.FileMetadataRepository;
import app.repository.FileSearchRepository;
import app.repository.FileWriteRepository;
import app.repository.IndexRunRepository;
import app.repository.SearchActivityRepository;

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

    public FileSearchRepository openFileSearch(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public SearchActivityRepository openSearchActivity(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public IndexRunRepository openIndexRuns(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public FileWriteRepository openFileWrite(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }

    public FileMetadataRepository openFileMetadata(String dbPath) throws SQLException, IOException {
        return openDatabase(dbPath);
    }
}
