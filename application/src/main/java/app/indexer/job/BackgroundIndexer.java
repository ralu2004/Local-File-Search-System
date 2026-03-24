package app.indexer.job;

import app.indexer.IndexReport;
import app.indexer.Indexer;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class BackgroundIndexer implements AutoCloseable {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile IndexingJobSnapshot snapshot = new IndexingJobSnapshot(
            IndexingJobStatus.IDLE, null, null, null, null
    );
    private volatile CompletableFuture<IndexReport> currentJob;

    public synchronized boolean start(Supplier<Indexer> indexerFactory) {
        if (isRunning()) return false;

        snapshot = new IndexingJobSnapshot(
                IndexingJobStatus.RUNNING, LocalDateTime.now(), null, snapshot.lastReport(), null
        );

        currentJob = CompletableFuture.supplyAsync(() -> indexerFactory.get().run(), executor)
                .whenComplete((report, error) -> {
                    if (error == null) {
                        snapshot = new IndexingJobSnapshot(
                                IndexingJobStatus.COMPLETED,
                                snapshot.startedAt(),
                                LocalDateTime.now(),
                                report,
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
                            message
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
