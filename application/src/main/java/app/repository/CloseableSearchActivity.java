package app.repository;

import java.sql.SQLException;

public interface CloseableSearchActivity extends SearchActivityRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
