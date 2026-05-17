package app.indexer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread safe counters used while an indexing run is in progress.
 */
class IndexingStats {
    final AtomicInteger totalFiles = new AtomicInteger();
    final AtomicInteger indexed    = new AtomicInteger();
    final AtomicInteger skipped    = new AtomicInteger();
    final AtomicInteger failed     = new AtomicInteger();
}
