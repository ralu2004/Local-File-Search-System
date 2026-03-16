package app.indexer;

import java.time.Duration;

public record IndexReport(
        int totalFiles,
        int indexed,
        int skipped,
        int failed,
        Duration elapsed
) {}