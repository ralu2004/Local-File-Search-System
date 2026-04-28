package app.repository;

import java.sql.SQLException;

public interface CloseableFileSearch extends FileSearchRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
