package app.service.search;

import app.db.Database;
import app.db.DatabaseProvider;
import app.repository.SearchActivityRepository;
import app.service.support.DatabaseAccessor;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Concrete implementation of {@link SearchObserver} that persists search execution activity
 * for analytics and history-based features.
 */
public final class SearchActivityObserver implements SearchObserver {

    private final DatabaseAccessor databaseAccessor;

    public SearchActivityObserver(DatabaseProvider databaseProvider) {
        this.databaseAccessor = new DatabaseAccessor(databaseProvider);
    }

    @Override
    public void onSearchExecuted(
            String dbPath,
            String rawQuery,
            String normalizedQuery,
            int resultCount,
            long durationMs,
            String executedAt
    ) throws SQLException, IOException {
        try (Database db = databaseAccessor.openDatabase(dbPath)) {
            SearchActivityRepository activity = db;
            activity.recordSearch(rawQuery, normalizedQuery, resultCount, durationMs, executedAt);
        }
    }
}
