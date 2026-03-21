package app.repository;

import app.indexer.IndexReport;
import app.model.IndexRun;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface IndexRunRepository {
    long startIndexing(LocalDateTime startedAt) throws SQLException;
    void endIndexing(long runId, IndexReport report) throws SQLException;
    List<IndexRun> getHistory() throws SQLException;
}