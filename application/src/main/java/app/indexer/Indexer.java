package app.indexer;

import app.crawler.Crawler;
import app.extractor.Extractor;
import app.extractor.FileTooLargeException;
import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.repository.FileRepository;
import app.repository.IndexRunRepository;

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

public class Indexer {

    private static final int DEFAULT_BATCH_SIZE = 250;
    private static final int PROGRESS_LOG_INTERVAL = 500;
    private static final int OPTIMIZE_FTS_MIN_INDEXED = 500;

    private enum IndexResult { QUEUED, SKIPPED, FAILED }

    private final Crawler crawler;
    private final Extractor extractor;
    private final FileRepository repository;
    private final IndexRunRepository indexRunRepository;
    private final int batchSize;

    public Indexer(FileRepository repository, IndexRunRepository indexRunRepository, Crawler crawler, Extractor extractor) {
        this(repository, indexRunRepository, crawler, extractor, DEFAULT_BATCH_SIZE);
    }

    public Indexer(FileRepository repository, IndexRunRepository indexRunRepository,
                   Crawler crawler, Extractor extractor, int batchSize) {
        this.repository = repository;
        this.indexRunRepository = indexRunRepository;
        this.crawler = crawler;
        this.extractor = extractor;
        this.batchSize = Math.max(1, batchSize);
    }

    public IndexReport run() {
        Instant start = Instant.now();
        long runId = 0;
        try {
            runId = indexRunRepository.startIndexing(LocalDateTime.now());
        } catch (SQLException e) {
            System.err.println("Failed to start index run tracking: " + e.getMessage());
        }

        IndexingStats stats = new IndexingStats();
        int deleted = 0;
        Set<Path> paths = new HashSet<>();
        List<ExtractedRecord> pendingBatch = new ArrayList<>(batchSize);
        final Map<Path, LocalDateTime> storedModifiedByPath = preloadStoredModifiedByPath();

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
                            System.err.println("Failed to write batch: " + e.getMessage());
                            pendingBatch.clear();
                        }
                    }
                }
                case SKIPPED -> stats.skipped++;
                case FAILED  -> stats.failed++;
            }
            if (currentTotal % PROGRESS_LOG_INTERVAL == 0) {
                System.out.println("Progress: " + currentTotal + " files processed...");
            }
        });

        try {
            if (!pendingBatch.isEmpty()) {
                stats.indexed += flushBatch(pendingBatch);
            }
            deleted = repository.batchDelete(paths);
            if (stats.indexed >= OPTIMIZE_FTS_MIN_INDEXED) {
                repository.optimizeFts();
            }
        } catch (SQLException e) {
            System.err.println("Index finalization failed: " + e.getMessage());
            stats.failed += pendingBatch.size();
        }

        Duration elapsed = Duration.between(start, Instant.now());
        IndexReport report = new IndexReport(
                stats.totalFiles, stats.indexed, stats.skipped, stats.failed, deleted, elapsed);

        try {
            indexRunRepository.endIndexing(runId, report);
        } catch (SQLException e) {
            System.err.println("Failed to finalize index run tracking: " + e.getMessage());
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
            System.err.println(e.getMessage());
            return IndexResult.SKIPPED;
        } catch (RuntimeException e) {
            System.err.println("Failed to index file: " + record.path() + " — " + e.getMessage());
            return IndexResult.FAILED;
        }
    }

    private int flushBatch(List<ExtractedRecord> pendingBatch) throws SQLException {
        int batchSize = pendingBatch.size();
        repository.batchUpsert(pendingBatch);
        pendingBatch.clear();
        return batchSize;
    }

    private Map<Path, LocalDateTime> preloadStoredModifiedByPath() {
        try {
            return repository.getAllModifiedAtByPath();
        } catch (SQLException e) {
            System.err.println("Failed to preload modified times: " + e.getMessage());
            return Map.of();
        }
    }
}