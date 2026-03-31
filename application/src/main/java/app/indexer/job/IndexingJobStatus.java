package app.indexer.job;

/**
 * Lifecycle states for background indexing execution.
 */
public enum IndexingJobStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED
}
