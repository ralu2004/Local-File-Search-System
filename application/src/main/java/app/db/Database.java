package app.db;

import app.db.sqlite.*;
import app.indexer.IndexReport;
import app.model.*;
import app.repository.FileRepository;
import app.repository.IndexRunRepository;
import app.repository.SearchActivityRepository;
import app.search.query.Query;
import app.search.ranking.RankingStrategy;

import java.io.IOException;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQLite-backed facade for file indexing, index-run history, and search activity.
 * Delegates to {@link app.db.sqlite.SqliteFileRepository},
 * {@link app.db.sqlite.SqliteIndexRunRepository}, and
 * {@link app.db.sqlite.SqliteSearchActivityRepository}; creates the DB file
 * parent directory and applies schema on construction.
 */
public class Database implements FileRepository, IndexRunRepository, SearchActivityRepository, AutoCloseable {

    private final SqliteConnectionProvider connections;
    private final SqliteFileRepository fileRepository;
    private final SqliteIndexRunRepository indexRunRepository;
    private final SqliteSearchActivityRepository searchActivityRepository;

    public Database(String dbPath) throws IOException, SQLException {
        Path path = Paths.get(dbPath);
        Files.createDirectories(path.getParent());

        String jdbcUrl = "jdbc:sqlite:" + dbPath;
        this.connections = new SqliteConnectionProvider(jdbcUrl);
        this.fileRepository = new SqliteFileRepository(connections);
        this.indexRunRepository = new SqliteIndexRunRepository(connections);
        this.searchActivityRepository = new SqliteSearchActivityRepository(connections);
        initializeSchema();
    }

    public Database() throws SQLException, IOException {
        this(System.getProperty("user.dir") + "/.searchengine/index.db");
    }

    private void initializeSchema() throws SQLException {
        try (Connection conn = getConnection()) {
            SchemaInitializer.initialize(conn);
        }
    }

    @Override
    public void upsert(FileRecord record, String content, String preview) throws SQLException {
        fileRepository.upsert(record, content, preview);
    }

    @Override
    public void batchUpsert(List<ExtractedRecord> records) throws SQLException {
        fileRepository.batchUpsert(records);
    }

    @Override
    public void delete(Path path) throws SQLException {
        fileRepository.delete(path);
    }

    @Override
    public int batchDelete(Set<Path> paths) throws SQLException {
        return fileRepository.batchDelete(paths);
    }

    @Override
    public List<SearchResult> search(Query query, int limit, RankingStrategy strategy, String normalizedQuery) throws SQLException {
        return fileRepository.search(query, limit, strategy, normalizedQuery);
    }

    @Override
    public LocalDateTime getModifiedAt(Path path) throws SQLException {
        return fileRepository.getModifiedAt(path);
    }

    @Override
    public Map<Path, LocalDateTime> getAllModifiedAtByPath() throws SQLException {
        return fileRepository.getAllModifiedAtByPath();
    }

    @Override
    public FileRecord getByPath(Path path) throws SQLException {
        return fileRepository.getByPath(path);
    }

    @Override
    public List<FileRecord> getAll() throws SQLException {
        return fileRepository.getAll();
    }

    @Override
    public List<FileRecord> getByExtension(String extension) throws SQLException {
        return fileRepository.getByExtension(extension);
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
    public void optimizeFts() throws SQLException {
        fileRepository.optimizeFts();
    }

    @Override
    public void recordSearch(String queryText, String normalizedQuery, int resultCount, long durationMs, String executedAt) throws SQLException {
        searchActivityRepository.recordSearch(queryText, normalizedQuery, resultCount, durationMs, executedAt);
    }

    @Override
    public void recordResultOpen(String queryText, String normalizedQuery, String filePath, Integer resultPosition, String openedAt) throws SQLException {
        searchActivityRepository.recordResultOpen(queryText, normalizedQuery, filePath, resultPosition, openedAt);
    }

    @Override
    public List<String> suggestQueries(String normalizedPrefix, int limit) throws SQLException {
        return searchActivityRepository.suggestQueries(normalizedPrefix, limit);
    }

    @Override
    public List<String> recentQueries(int limit) throws SQLException {
        return searchActivityRepository.recentQueries(limit);
    }

    private Connection getConnection() throws SQLException {
        return connections.open();
    }

    @Override
    public void close() {
    }
}