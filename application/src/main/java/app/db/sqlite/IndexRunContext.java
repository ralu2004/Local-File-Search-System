package app.db.sqlite;

import app.indexer.IndexReport;
import app.model.IndexRun;
import app.repository.CloseableIndexRuns;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence context for index run lifecycle and history.
 * Implements the {@link CloseableIndexRuns} view and delegates to
 * {@link SqliteIndexRunRepository}. Created and owned by
 * {@link SqliteDatabaseSession}.
 */
public final class IndexRunContext implements CloseableIndexRuns {

    private final SqliteIndexRunRepository indexRunRepository;

    IndexRunContext(SqliteConnectionProvider connections) {
        this.indexRunRepository = new SqliteIndexRunRepository(connections);
    }

    @Override
    public long startIndexing(LocalDateTime startedAt, String rootPath) throws SQLException {
        return indexRunRepository.startIndexing(startedAt, rootPath);
    }

    @Override
    public void endIndexing(long runId, IndexReport report) throws SQLException {
        indexRunRepository.endIndexing(runId, report);
    }

    @Override
    public List<IndexRun> getHistory() throws SQLException {
        return indexRunRepository.getHistory();
    }

    @Override
    public List<IndexRun> getHistory(int limit) throws SQLException {
        return indexRunRepository.getHistory(limit);
    }

    @Override
    public void close() throws SQLException {
        // there are no long-lived resources; connections are per-method via SqliteConnectionProvider.
    }
}
