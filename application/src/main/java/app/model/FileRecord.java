package app.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Immutable file metadata captured during crawling.
 */
public record FileRecord(
        Path path,
        String filename,
        String extension,
        long sizeBytes,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt
) {}