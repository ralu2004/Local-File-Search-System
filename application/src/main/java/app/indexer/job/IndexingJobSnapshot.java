package app.indexer.job;

import app.indexer.IndexReport;

import java.time.LocalDateTime;

public record IndexingJobSnapshot(
        IndexingJobStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        IndexReport lastReport,
        String lastError
) {}
