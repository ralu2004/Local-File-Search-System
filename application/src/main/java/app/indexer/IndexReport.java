package app.indexer;

import java.time.Duration;

/**
 * Final aggregated metrics produced by an indexing run.
 */
public record IndexReport(
        int totalFiles,
        int indexed,
        int skipped,
        int failed,
        int deleted,
        Duration elapsed
) {}