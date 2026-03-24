package app.repository;

import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.model.SearchResult;
import app.search.query.Query;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface FileRepository {
    void upsert(FileRecord record, String content, String preview) throws SQLException;
    void batchUpsert(List<ExtractedRecord> records) throws SQLException;
    void delete(Path path) throws SQLException;
    int batchDelete(Set<Path> paths) throws SQLException;
    List<SearchResult> search(Query query, int limit) throws SQLException;
    LocalDateTime getModifiedAt(Path path) throws SQLException;
    Map<Path, LocalDateTime> getAllModifiedAtByPath() throws SQLException;
    FileRecord getByPath(Path path) throws SQLException;
    List<FileRecord> getAll() throws SQLException;
    List<FileRecord> getByExtension(String extension) throws SQLException;
    void optimizeFts() throws SQLException;
}
