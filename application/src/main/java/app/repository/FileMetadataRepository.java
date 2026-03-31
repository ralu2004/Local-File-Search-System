package app.repository;

import app.model.FileRecord;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Metadata read operations for indexed files.
 */
public interface FileMetadataRepository {
    LocalDateTime getModifiedAt(Path path) throws SQLException;
    Map<Path, LocalDateTime> getAllModifiedAtByPath() throws SQLException;
    FileRecord getByPath(Path path) throws SQLException;
    List<FileRecord> getAll() throws SQLException;
    List<FileRecord> getByExtension(String extension) throws SQLException;
}
