package app.model;

import app.search.ranking.PathFeatures;

/**
 * File metadata paired with extracted full content, preview, path features,
 * and an optional dominant color for image files.
 * <p>
 * {@code dominantColor} is non-null only for image files processed by
 * {@link app.extractor.strategy.ImageFileStrategy}.
 */
public record ExtractedRecord(
        FileRecord record,
        String content,
        String preview,
        PathFeatures features,
        String dominantColor
) {}
