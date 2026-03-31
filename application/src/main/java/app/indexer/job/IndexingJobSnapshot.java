package app.indexer.job;

import app.indexer.IndexReport;

import java.time.LocalDateTime;

/**
 * Immutable view of the current/last background indexing job state.
 */
public record IndexingJobSnapshot(
        IndexingJobStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        IndexReport lastReport,
        String lastError,
        IndexingLiveProgress liveProgress
) {}
