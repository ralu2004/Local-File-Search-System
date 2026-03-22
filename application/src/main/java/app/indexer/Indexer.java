package app.indexer;

import app.crawler.Crawler;
import app.extractor.Extractor;
import app.extractor.FileTooLargeException;
import app.model.FileRecord;
import app.repository.FileRepository;
import app.repository.IndexRunRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

public class Indexer {

    private enum IndexResult { INDEXED, SKIPPED, FAILED }

    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private final Crawler crawler;
    private final Extractor extractor;
    private final FileRepository repository;
    private final IndexRunRepository indexRunRepository;
    private final int threadCount;

    public Indexer(FileRepository repository, IndexRunRepository indexRunRepository,
                   Crawler crawler, Extractor extractor) {
        this(repository, indexRunRepository, crawler, extractor, DEFAULT_THREAD_COUNT);
    }

    public Indexer(FileRepository repository, IndexRunRepository indexRunRepository,
                   Crawler crawler, Extractor extractor, int threadCount) {
        this.repository = repository;
        this.indexRunRepository = indexRunRepository;
        this.crawler = crawler;
        this.extractor = extractor;
        this.threadCount = threadCount;
    }

    public IndexReport run() {
        Instant start = Instant.now();
        long runId = 0;

        try {
            runId = indexRunRepository.startIndexing(LocalDateTime.now());
        } catch (SQLException e) {
            System.err.println("Failed to start index run tracking: " + e.getMessage());
        }

        AtomicInteger totalFiles = new AtomicInteger();
        AtomicInteger indexed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        Set<Path> paths = ConcurrentHashMap.newKeySet();

        try (ForkJoinPool pool = new ForkJoinPool(threadCount);) {
            pool.submit(() ->
                    StreamSupport.stream(
                                    ((Iterable<FileRecord>) crawler.crawl()::iterator).spliterator(), true)
                            .forEach(record -> {
                                paths.add(record.path());
                                switch (indexFile(record)) {
                                    case INDEXED -> indexed.incrementAndGet();
                                    case SKIPPED -> skipped.incrementAndGet();
                                    case FAILED  -> failed.incrementAndGet();
                                }
                                int total = totalFiles.incrementAndGet();
                                if (total % 100 == 0) {
                                    System.out.println("Progress: " + total + " files processed...");
                                }
                            })
            ).get();
        } catch (Exception e) {
            System.err.println("Error during parallel indexing: " + e.getMessage());
        }

        int deleted = 0;
        try {
            deleted = repository.batchDelete(paths);
            repository.optimizeFts();
        } catch (SQLException e) {
            System.err.println("Something went wrong while deleting files: " + e.getMessage());
        }

        Duration elapsed = Duration.between(start, Instant.now());
        IndexReport report = new IndexReport(
                totalFiles.get(), indexed.get(), skipped.get(),
                failed.get(), deleted, elapsed);

        try {
            indexRunRepository.endIndexing(runId, report);
        } catch (SQLException e) {
            System.err.println("Failed to finalize index run tracking: " + e.getMessage());
        }

        return report;
    }

    private IndexResult indexFile(FileRecord record) {
        try {
            LocalDateTime storedModifiedAt = repository.getModifiedAt(record.path());
            if (storedModifiedAt != null && storedModifiedAt.equals(record.modifiedAt())) {
                return IndexResult.SKIPPED;
            }
            String content = extractor.extract(record);
            String preview = extractor.preview(record);
            repository.upsert(record, content, preview);
            return IndexResult.INDEXED;
        } catch (FileTooLargeException e) {
            System.err.println(e.getMessage());
            return IndexResult.SKIPPED;
        } catch (SQLException e) {
            System.err.println("Failed to index file: " + record.path() + " — " + e.getMessage());
            return IndexResult.FAILED;
        }
    }
}