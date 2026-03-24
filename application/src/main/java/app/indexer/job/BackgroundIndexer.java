package app.indexer.job;

import app.indexer.IndexReport;
import app.indexer.Indexer;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class BackgroundIndexer implements AutoCloseable {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile IndexingJobSnapshot snapshot = new IndexingJobSnapshot(
            IndexingJobStatus.IDLE, null, null, null, null, null
    );
    private volatile CompletableFuture<IndexReport> currentJob;

    public synchronized boolean start(Function<IndexingLiveProgress, Indexer> indexerFactory) {
        if (isRunning()) return false;

        IndexingLiveProgress live = new IndexingLiveProgress();
        snapshot = new IndexingJobSnapshot(
                IndexingJobStatus.RUNNING,
                LocalDateTime.now(),
                null,
                snapshot.lastReport(),
                null,
                live
        );

        currentJob = CompletableFuture.supplyAsync(() -> indexerFactory.apply(live).run(), executor)
                .whenComplete((report, error) -> {
                    if (error == null) {
                        snapshot = new IndexingJobSnapshot(
                                IndexingJobStatus.COMPLETED,
                                snapshot.startedAt(),
                                LocalDateTime.now(),
                                report,
                                null,
                                null
                        );
                        return;
                    }
                    String message = error.getCause() != null ? error.getCause().getMessage() : error.getMessage();
                    snapshot = new IndexingJobSnapshot(
                            IndexingJobStatus.FAILED,
                            snapshot.startedAt(),
                            LocalDateTime.now(),
                            snapshot.lastReport(),
                            message,
                            null
                    );
                });
        return true;
    }

    public IndexingJobSnapshot getSnapshot() {
        return snapshot;
    }

    public boolean isRunning() {
        return currentJob != null && !currentJob.isDone();
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
