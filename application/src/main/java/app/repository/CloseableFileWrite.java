package app.repository;

import java.sql.SQLException;

public interface CloseableFileWrite extends FileWriteRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
