package app.db.sqlite;

import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.model.RankedSearchResult;
import app.repository.CloseableFileMetadata;
import app.repository.CloseableFileSearch;
import app.repository.CloseableFileWrite;
import app.search.query.Query;
import app.search.ranking.RankingStrategy;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistence context for file records. Implements all closeable
 * views relevant to file operations (search, write, metadata) and delegates
 * to {@link SqliteFileRepository}. Created and owned by
 * {@link SqliteDatabaseSession}.
 */
public final class FileContext implements CloseableFileSearch, CloseableFileWrite, CloseableFileMetadata {

    private final SqliteFileRepository fileRepository;

    FileContext(SqliteConnectionProvider connections) {
        this.fileRepository = new SqliteFileRepository(connections);
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
    public List<RankedSearchResult> search(Query query, int limit, RankingStrategy strategy, String normalizedQuery) throws SQLException {
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
    public void optimizeFts() throws SQLException {
        fileRepository.optimizeFts();
    }

    @Override
    public void close() throws SQLException {
        // there are no long-lived resources; connections are per-method via SqliteConnectionProvider.
    }
}
