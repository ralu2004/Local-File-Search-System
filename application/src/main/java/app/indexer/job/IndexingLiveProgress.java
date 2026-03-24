package app.indexer.job;

public final class IndexingLiveProgress {

    private volatile int totalFiles;
    private volatile int indexed;
    private volatile int skipped;
    private volatile int failed;
    private volatile int pendingInBatch;
    private volatile String phase = "crawling";

    public int getTotalFiles() {
        return totalFiles;
    }

    public int getIndexed() {
        return indexed;
    }

    public int getSkipped() {
        return skipped;
    }

    public int getFailed() {
        return failed;
    }

    public int getPendingInBatch() {
        return pendingInBatch;
    }

    public String getPhase() {
        return phase;
    }

    public void publish(int totalFiles, int indexed, int skipped, int failed, int pendingInBatch) {
        this.totalFiles = totalFiles;
        this.indexed = indexed;
        this.skipped = skipped;
        this.failed = failed;
        this.pendingInBatch = pendingInBatch;
    }

    public void setPhase(String phase) {
        this.phase = phase == null ? "crawling" : phase;
    }
}
