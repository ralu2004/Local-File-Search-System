package app.repository;

import java.sql.SQLException;

public interface CloseableFileMetadata extends FileMetadataRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
