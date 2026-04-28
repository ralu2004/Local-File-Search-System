package app.repository;

import java.sql.SQLException;

public interface CloseableIndexRuns extends IndexRunRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
