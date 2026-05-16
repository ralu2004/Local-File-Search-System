package app.repository;

import java.sql.SQLException;

public interface CloseableImageFeature extends ImageFeatureRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
