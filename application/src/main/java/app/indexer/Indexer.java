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

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates one indexing run: crawl files, extract content, write batches,
 * remove stale entries, and report progress/statistics.
 */
public class Indexer {

    private static final Logger log = LoggerFactory.getLogger(Indexer.class);
    private static final int DEFAULT_BATCH_SIZE = 250;
    private static final int PROGRESS_LOG_INTERVAL = 500;
    private static final int OPTIMIZE_FTS_MIN_INDEXED = 500;

    /**
     * Outcome of attempting to index a single crawled file.
     */
    private enum IndexResult { QUEUED, SKIPPED, FAILED }

    private final Crawler crawler;
    private final Extractor extractor;
    private final FileWriteRepository writeRepository;
    private final FileMetadataRepository metadataRepository;
    private final IndexRunRepository indexRunRepository;
    private final int batchSize;
    private final IndexingLiveProgress liveProgress;

    public Indexer(FileWriteRepository writeRepository, FileMetadataRepository metadataRepository,
                   IndexRunRepository indexRunRepository, Crawler crawler, Extractor extractor) {
        this(writeRepository, metadataRepository, indexRunRepository, crawler, extractor, DEFAULT_BATCH_SIZE, null);
    }

    public Indexer(FileWriteRepository writeRepository, FileMetadataRepository metadataRepository,
                   IndexRunRepository indexRunRepository,
                   Crawler crawler, Extractor extractor, int batchSize) {
        this(writeRepository, metadataRepository, indexRunRepository, crawler, extractor, batchSize, null);
    }

    public Indexer(FileWriteRepository writeRepository, FileMetadataRepository metadataRepository,
                   IndexRunRepository indexRunRepository,
                   Crawler crawler, Extractor extractor, int batchSize,
                   IndexingLiveProgress liveProgress) {
        this.writeRepository = writeRepository;
        this.metadataRepository = metadataRepository;
        this.indexRunRepository = indexRunRepository;
        this.crawler = crawler;
        this.extractor = extractor;
        this.batchSize = Math.max(1, batchSize);
        this.liveProgress = liveProgress;
    }

    public IndexReport run() {
        Instant start = Instant.now();
        long runId = 0;
        try {
            runId = indexRunRepository.startIndexing(
                    LocalDateTime.now(),
                    crawler.getRoot().toAbsolutePath().normalize().toString());
        } catch (SQLException e) {
            log.warn("Failed to start index run tracking: {}", e.getMessage());
        }

        IndexingStats stats = new IndexingStats();
        int deleted = 0;
        Set<Path> paths = new HashSet<>();
        List<ExtractedRecord> pendingBatch = new ArrayList<>(batchSize);
        final Map<Path, LocalDateTime> storedModifiedByPath = preloadStoredModifiedByPath();

        if (liveProgress != null) {
            liveProgress.setPhase("crawling");
            publishLive(stats, pendingBatch.size());
        }

        crawler.crawl(record -> {
            int currentTotal = ++stats.totalFiles;
            paths.add(record.path());
            switch (indexFile(record, pendingBatch, storedModifiedByPath)) {
                case QUEUED -> {
                    if (pendingBatch.size() >= batchSize) {
                        try {
                            stats.indexed += flushBatch(pendingBatch);
                        } catch (SQLException e) {
                            stats.failed += pendingBatch.size();
                            log.error("Failed to write DB batch of size {}: {}", pendingBatch.size(), e.getMessage());
                            pendingBatch.clear();
                        }
                    }
                }
                case SKIPPED -> stats.skipped++;
                case FAILED  -> stats.failed++;
            }
            if (liveProgress != null && (currentTotal % 20 == 0 || currentTotal <= 3)) {
                publishLive(stats, pendingBatch.size());
            }
            if (currentTotal % PROGRESS_LOG_INTERVAL == 0) {
                log.info("Progress: {} files processed...", currentTotal);
            }
        });

        if (liveProgress != null) {
            publishLive(stats, pendingBatch.size());
            liveProgress.setPhase("finalizing");
            publishLive(stats, pendingBatch.size());
        }

        try {
            if (!pendingBatch.isEmpty()) {
                stats.indexed += flushBatch(pendingBatch);
            }
            if (liveProgress != null) {
                publishLive(stats, pendingBatch.size());
            }
            deleted = writeRepository.batchDelete(paths);
            if (liveProgress != null) {
                publishLive(stats, pendingBatch.size());
            }
            if (stats.indexed >= OPTIMIZE_FTS_MIN_INDEXED) {
                writeRepository.optimizeFts();
            }
        } catch (SQLException e) {
            log.error("Index finalization failed: {}", e.getMessage());
            stats.failed += pendingBatch.size();
        }

        if (liveProgress != null) {
            publishLive(stats, pendingBatch.size());
        }

        Duration elapsed = Duration.between(start, Instant.now());
        IndexReport report = new IndexReport(
                stats.totalFiles, stats.indexed, stats.skipped, stats.failed, deleted, elapsed);

        try {
            indexRunRepository.endIndexing(runId, report);
        } catch (SQLException e) {
            log.warn("Failed to finalize index run tracking: {}", e.getMessage());
        }

        return report;
    }

    private IndexResult indexFile(FileRecord record, List<ExtractedRecord> pendingBatch,
                                  Map<Path, LocalDateTime> storedModifiedByPath) {
        try {
            LocalDateTime storedModifiedAt = storedModifiedByPath.get(record.path());
            if (storedModifiedAt != null && storedModifiedAt.equals(record.modifiedAt())) {
                return IndexResult.SKIPPED;
            }
            ExtractedRecord extracted = extractor.extractWithPreview(record);
            pendingBatch.add(extracted);
            return IndexResult.QUEUED;
        } catch (FileTooLargeException e) {
            log.debug("Skipping large file: {}", e.getMessage());
            return IndexResult.SKIPPED;
        } catch (RuntimeException e) {
            log.warn("Failed to index file {}: {}", record.path(), e.getMessage());
            return IndexResult.FAILED;
        }
    }

    private int flushBatch(List<ExtractedRecord> pendingBatch) throws SQLException {
        int batchSize = pendingBatch.size();
        writeRepository.batchUpsert(pendingBatch);
        pendingBatch.clear();
        return batchSize;
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
        liveProgress.publish(stats.totalFiles, stats.indexed, stats.skipped, stats.failed, pendingBatchSize);
    }
}