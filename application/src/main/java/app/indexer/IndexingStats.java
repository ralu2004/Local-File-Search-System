package app.indexer;

/**
 * Mutable counters used while an indexing run is in progress.
 */
class IndexingStats {
    int totalFiles;
    int indexed;
    int skipped;
    int failed;
}
