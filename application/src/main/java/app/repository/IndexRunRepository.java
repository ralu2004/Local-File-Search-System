package app.repository;

import app.indexer.IndexReport;
import app.model.IndexRun;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence contract for indexing run lifecycle and history.
 */
public interface IndexRunRepository {
    long startIndexing(LocalDateTime startedAt, String rootPath) throws SQLException;
    void endIndexing(long runId, IndexReport report) throws SQLException;
    List<IndexRun> getHistory() throws SQLException;
    List<IndexRun> getHistory(int limit) throws SQLException;
}