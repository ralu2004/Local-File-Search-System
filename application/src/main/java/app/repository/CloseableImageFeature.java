package app.repository;

import java.sql.SQLException;

/**
 * Closeable view of {@link ImageFeatureRepository}.
 * Follows the same pattern as other closeable repository
 * views ({@link CloseableFileWrite}, {@link CloseableFileSearch}, etc.)
 * to allow try-with-resources usage in services.
 */
public interface CloseableImageFeature extends ImageFeatureRepository, AutoCloseable {
    @Override
    void close() throws SQLException;
}
