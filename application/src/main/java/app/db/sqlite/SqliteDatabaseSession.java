package app.db.sqlite;

import app.db.DatabaseSession;
import app.indexer.IndexReport;
import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.model.IndexRun;
import app.model.RankedSearchResult;
import app.search.query.Query;
import app.search.ranking.RankingStrategy;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQLite-backed implementation of {@link DatabaseSession} composed from
 * three per-domain contexts ({@link FileContext}, {@link IndexRunContext},
 * {@link ActivityContext}) sharing a single {@link SqliteConnectionProvider}.
 *
 * <p>This class is the only place where all six closeable views meet. It
 * exists because services need a single handle to the persistence subsystem
 * for transactional scoping, even though each service operation only consumes
 * a narrow view. Each method here is a delegation to one of the three
 * underlying contexts.
 */
public final class SqliteDatabaseSession implements DatabaseSession {

    private final FileContext fileContext;
    private final IndexRunContext indexRunContext;
    private final ActivityContext activityContext;

    public SqliteDatabaseSession(SqliteConnectionProvider connections) {
        this.fileContext = new FileContext(connections);
        this.indexRunContext = new IndexRunContext(connections);
        this.activityContext = new ActivityContext(connections);
    }

    @Override
    public void upsert(FileRecord record, String content, String preview) throws SQLException {
        fileContext.upsert(record, content, preview);
    }

    @Override
    public void batchUpsert(List<ExtractedRecord> records) throws SQLException {
        fileContext.batchUpsert(records);
    }

    @Override
    public void delete(Path path) throws SQLException {
        fileContext.delete(path);
    }

    @Override
    public int batchDelete(Set<Path> paths) throws SQLException {
        return fileContext.batchDelete(paths);
    }

    @Override
    public List<RankedSearchResult> search(Query query, int limit, RankingStrategy strategy, String normalizedQuery) throws SQLException {
        return fileContext.search(query, limit, strategy, normalizedQuery);
    }

    @Override
    public LocalDateTime getModifiedAt(Path path) throws SQLException {
        return fileContext.getModifiedAt(path);
    }

    @Override
    public Map<Path, LocalDateTime> getAllModifiedAtByPath() throws SQLException {
        return fileContext.getAllModifiedAtByPath();
    }

    @Override
    public FileRecord getByPath(Path path) throws SQLException {
        return fileContext.getByPath(path);
    }

    @Override
    public List<FileRecord> getAll() throws SQLException {
        return fileContext.getAll();
    }

    @Override
    public List<FileRecord> getByExtension(String extension) throws SQLException {
        return fileContext.getByExtension(extension);
    }

    @Override
    public void optimizeFts() throws SQLException {
        fileContext.optimizeFts();
    }

    @Override
    public long startIndexing(LocalDateTime startedAt, String rootPath) throws SQLException {
        return indexRunContext.startIndexing(startedAt, rootPath);
    }

    @Override
    public void endIndexing(long runId, IndexReport report) throws SQLException {
        indexRunContext.endIndexing(runId, report);
    }

    @Override
    public List<IndexRun> getHistory() throws SQLException {
        return indexRunContext.getHistory();
    }

    @Override
    public List<IndexRun> getHistory(int limit) throws SQLException {
        return indexRunContext.getHistory(limit);
    }

    @Override
    public void recordSearch(String queryText, String normalizedQuery, int resultCount, long durationMs, String executedAt) throws SQLException {
        activityContext.recordSearch(queryText, normalizedQuery, resultCount, durationMs, executedAt);
    }

    @Override
    public void recordResultOpen(String queryText, String normalizedQuery, String filePath, Integer resultPosition, String openedAt) throws SQLException {
        activityContext.recordResultOpen(queryText, normalizedQuery, filePath, resultPosition, openedAt);
    }

    @Override
    public List<String> suggestQueries(String normalizedPrefix, int limit) throws SQLException {
        return activityContext.suggestQueries(normalizedPrefix, limit);
    }

    @Override
    public List<String> recentQueries(int limit) throws SQLException {
        return activityContext.recentQueries(limit);
    }

    @Override
    public void close() throws SQLException {
        // Each context's close() is a no-op today (connections are per-method),
        // but we call them in case they grow long-lived state later.
        fileContext.close();
        indexRunContext.close();
        activityContext.close();
    }
}
