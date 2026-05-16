package app.repository;

import java.sql.SQLException;

/**
 * Persistence contract for image files.
 */
public interface ImageFeatureRepository {
    void upsertImageFeature(String path, String dominantColor) throws SQLException;
}