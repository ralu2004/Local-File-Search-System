package app.repository;

import app.model.ExtractedRecord;
import app.model.FileRecord;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public interface FileWriteRepository {
    void upsert(FileRecord record, String content, String preview) throws SQLException;
    void batchUpsert(List<ExtractedRecord> records) throws SQLException;
    void delete(Path path) throws SQLException;
    int batchDelete(Set<Path> paths) throws SQLException;
    void optimizeFts() throws SQLException;
}
