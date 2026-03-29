package app.service;

import app.db.Database;
import app.db.DatabaseProvider;
import app.model.IndexRun;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Application service for index history queries.
 */
public class HistoryService {
    private final DatabaseProvider databaseProvider;

    public HistoryService(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    public List<IndexRun> getHistory(String dbPath) throws SQLException, IOException {
        try (Database db = openDatabase(dbPath)) {
            return db.getHistory();
        }
    }

    private Database openDatabase(String dbPath) throws SQLException, IOException {
        if (dbPath == null || dbPath.isBlank()) {
            return databaseProvider.openDefault();
        }
        return databaseProvider.open(dbPath);
    }
}
