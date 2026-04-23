package app.model;

import app.search.ranking.PathFeatures;

/**
 * File metadata paired with extracted full content, preview, and path features.
 * <p>
 * {@link PathFeatures} are computed at index time by {@link app.indexer.PathFeatureExtractor}
 * and persisted to the {@code path_features} table for query-time ranking.
 */
public record ExtractedRecord(FileRecord record, String content, String preview, PathFeatures features) {}