package app.model;

import java.time.Duration;
import java.time.LocalDateTime;

public record IndexRun(
        long id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalFiles,
        int indexed,
        int skipped,
        int failed,
        int deleted,
        Duration elapsed
) {}