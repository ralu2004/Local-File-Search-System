package app.indexer;

import app.crawler.Crawler;
import app.extractor.Extractor;
import app.extractor.FileTooLargeException;
import app.indexer.job.IndexingLiveProgress;
import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.repository.FileMetadataRepository;
import app.repository.FileWriteRepository;
import app.repository.IndexRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates one indexing run using a Producer-Consumer architecture.
 * <p>
 * The crawler emits file records to a thread pool of readers that extract
 * content in parallel. Extracted records are queued and consumed by
 * {@link IndexWriter}, which commits batches to the database on a dedicated
 * thread, avoiding SQLite write contention.
 */
public class Indexer {

    private static final Logger log = LoggerFactory.getLogger(Indexer.class);
    private static final ExtractedRecord POISON_PILL = new ExtractedRecord(null, null, null, null, null);
    private static final int PROGRESS_LOG_INTERVAL = 500;
    private static final int OPTIMIZE_FTS_MIN_INDEXED = 500;

    private final Crawler crawler;
    private final Extractor extractor;
    private final FileWriteRepository writeRepository;
    private final FileMetadataRepository metadataRepository;
    private final IndexRunRepository indexRunRepository;
    private final int batchSize;
    private final IndexingLiveProgress liveProgress;
    private final IndexWriter indexWriter;

    public Indexer(FileWriteRepository writeRepository, FileMetadataRepository metadataRepository, IndexRunRepository indexRunRepository,
                   Crawler crawler, Extractor extractor, int batchSize, IndexingLiveProgress liveProgress) {
        this.writeRepository = writeRepository;
        this.metadataRepository = metadataRepository;
        this.indexRunRepository = indexRunRepository;
        this.crawler = crawler;
        this.extractor = extractor;
        this.batchSize = Math.max(1, batchSize);
        this.liveProgress = liveProgress;
        this.indexWriter = new IndexWriter(writeRepository, batchSize);
    }

    /**
     * Runs one full indexing cycle: crawl, extract, write, finalize.
     */
    public IndexReport run() {
        Instant start = Instant.now();
        long runId = startRun();

        IndexingStats stats = new IndexingStats();
        Set<Path> paths = ConcurrentHashMap.newKeySet();
        Map<Path, LocalDateTime> storedModifiedByPath = preloadStoredModifiedByPath();

        runPipeline(stats, paths, storedModifiedByPath);
        int deleted = finalize(stats, paths);

        IndexReport report = new IndexReport(
                stats.totalFiles.get(), stats.indexed.get(),
                stats.skipped.get(), stats.failed.get(),
                deleted, Duration.between(start, Instant.now()));

        endRun(runId, report);
        return report;
    }

    /**
     * Runs the Producer-Consumer pipeline: starts the writer thread,
     * crawls files submitting extraction tasks to the reader pool,
     * then shuts everything down.
     */
    private void runPipeline(IndexingStats stats, Set<Path> paths, Map<Path, LocalDateTime> storedModifiedByPath) {
        int poolSize = Runtime.getRuntime().availableProcessors();
        BlockingQueue<ExtractedRecord> queue = new LinkedBlockingQueue<>(poolSize * batchSize);

        if (liveProgress != null) {
            liveProgress.setPhase("crawling");
            publishLive(stats, 0);
        }

        Thread writerThread = indexWriter.start(queue, POISON_PILL, stats, liveProgress);
        ExecutorService readers = Executors.newFixedThreadPool(poolSize);

        try {
            crawler.crawl(record -> submitExtractionTask(record, readers, queue, paths, storedModifiedByPath, stats));
            readers.shutdown();
            readers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            queue.put(POISON_PILL);
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Indexer interrupted during pipeline");
        } finally {
            if (!readers.isTerminated()) {
                readers.shutdownNow();
            }
        }
    }

    /**
     * Submits a single file extraction task to the reader pool.
     * Skips files that have not changed since the last index run.
     */
    private void submitExtractionTask(FileRecord record, ExecutorService readers, BlockingQueue<ExtractedRecord> queue, Set<Path> paths,
                                      Map<Path, LocalDateTime> storedModifiedByPath, IndexingStats stats) {
        int currentTotal = stats.totalFiles.incrementAndGet();
        paths.add(record.path());

        LocalDateTime stored = storedModifiedByPath.get(record.path());
        if (stored != null && stored.equals(record.modifiedAt())) {
            stats.skipped.incrementAndGet();
            return;
        }

        readers.submit(() -> {
            try {
                ExtractedRecord extracted = extractor.extractWithPreview(record);
                queue.put(extracted);
            } catch (FileTooLargeException e) {
                stats.skipped.incrementAndGet();
                log.debug("Skipping large file: {}", e.getMessage());
            } catch (IOException | RuntimeException e) {
                stats.failed.incrementAndGet();
                log.warn("Failed to index file {}: {}", record.path(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        if (liveProgress != null && (currentTotal % 20 == 0 || currentTotal <= 3)) {
            publishLive(stats, 0);
        }
        if (currentTotal % PROGRESS_LOG_INTERVAL == 0) {
            log.info("Progress: {} files processed...", currentTotal);
        }
    }

    /**
     * Deletes stale entries and optimizes FTS after the pipeline completes.
     */
    private int finalize(IndexingStats stats, Set<Path> paths) {
        if (liveProgress != null) {
            liveProgress.setPhase("finalizing");
            publishLive(stats, 0);
        }

        int deleted = 0;
        try {
            deleted = writeRepository.batchDelete(paths);
            if (stats.indexed.get() >= OPTIMIZE_FTS_MIN_INDEXED) {
                writeRepository.optimizeFts();
            }
        } catch (SQLException e) {
            log.error("Index finalization failed: {}", e.getMessage());
        }
        return deleted;
    }

    private long startRun() {
        try {
            return indexRunRepository.startIndexing(LocalDateTime.now(), crawler.getRoot().toAbsolutePath().normalize().toString());
        } catch (SQLException e) {
            log.warn("Failed to start index run tracking: {}", e.getMessage());
            return 0;
        }
    }

    private void endRun(long runId, IndexReport report) {
        try {
            indexRunRepository.endIndexing(runId, report);
        } catch (SQLException e) {
            log.warn("Failed to finalize index run tracking: {}", e.getMessage());
        }
    }

    private Map<Path, LocalDateTime> preloadStoredModifiedByPath() {
        try {
            return metadataRepository.getAllModifiedAtByPath();
        } catch (SQLException e) {
            log.warn("Failed to preload modified times: {}", e.getMessage());
            return Map.of();
        }
    }

    private void publishLive(IndexingStats stats, int pendingBatchSize) {
        if (liveProgress == null) return;
        liveProgress.publish(stats.totalFiles.get(), stats.indexed.get(), stats.skipped.get(), stats.failed.get(), pendingBatchSize);
    }
}