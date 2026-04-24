package app.service.search;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Observer contract for search lifecycle events.
 */
public interface SearchObserver {

    /**
     * Called after a search execution completes.
     */
    void onSearchExecuted(
            String dbPath,
            String rawQuery,
            String normalizedQuery,
            int resultCount,
            long durationMs,
            String executedAt
    ) throws SQLException, IOException;
}
