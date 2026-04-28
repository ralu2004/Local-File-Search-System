package app.repository;

import java.sql.SQLException;

public interface CloseableIndexSession extends
        FileWriteRepository, FileMetadataRepository, IndexRunRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
