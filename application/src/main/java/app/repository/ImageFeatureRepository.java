package app.repository;

import java.sql.SQLException;

/**
 * Persistence contract for image-derived metadata.
 * Implemented by repositories that store dominant color
 * extracted from image files during indexing.
 */
public interface ImageFeatureRepository {
    void upsertImageFeature(String path, String dominantColor) throws SQLException;
}