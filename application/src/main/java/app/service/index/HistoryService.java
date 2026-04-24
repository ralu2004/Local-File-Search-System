package app.service.index;

import app.db.Database;
import app.db.DatabaseProvider;
import app.model.IndexRun;
import app.service.support.DatabaseAccessor;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Application service for index history queries.
 */
public class HistoryService {
    private final DatabaseAccessor databaseAccessor;

    public HistoryService(DatabaseProvider databaseProvider) {
        this.databaseAccessor = new DatabaseAccessor(databaseProvider);
    }

    public List<IndexRun> getHistory(String dbPath) throws SQLException, IOException {
        try (Database db = databaseAccessor.openDatabase(dbPath)) {
            return db.getHistory();
        }
    }
}
