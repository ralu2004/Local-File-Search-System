package app.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Persisted summary of one indexing execution.
 */
public record IndexRun(
        long id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String rootPath,
        int totalFiles,
        int indexed,
        int skipped,
        int failed,
        int deleted,
        Duration elapsed
) {}