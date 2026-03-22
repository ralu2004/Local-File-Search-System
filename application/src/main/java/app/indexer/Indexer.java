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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

public class Indexer {

    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int WRITE_QUEUE_CAPACITY = 1000;

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

        BlockingQueue<ExtractedRecord> writeQueue = new LinkedBlockingQueue<>(WRITE_QUEUE_CAPACITY);
        AtomicBoolean extractionDone = new AtomicBoolean(false);

        Thread writerThread = Thread.ofVirtual().start(() -> {
            while (!extractionDone.get() || !writeQueue.isEmpty()) {
                try {
                    ExtractedRecord extracted = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (extracted == null) continue;
                    repository.upsert(extracted.record(), extracted.content(), extracted.preview());
                    indexed.incrementAndGet();
                } catch (SQLException e) {
                    failed.incrementAndGet();
                    System.err.println("Failed to write: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        try (ForkJoinPool pool = new ForkJoinPool(threadCount)) {
            pool.submit(() ->
                    StreamSupport.stream(
                                    ((Iterable<FileRecord>) crawler.crawl()::iterator).spliterator(), true)
                            .forEach(record -> {
                                paths.add(record.path());
                                int total = totalFiles.incrementAndGet();
                                if (total % 100 == 0) {
                                    System.out.println("Progress: " + total + " files processed...");
                                }
                                try {
                                    LocalDateTime storedModifiedAt = repository.getModifiedAt(record.path());
                                    if (storedModifiedAt != null && storedModifiedAt.equals(record.modifiedAt())) {
                                        skipped.incrementAndGet();
                                        return;
                                    }
                                    String content = extractor.extract(record);
                                    String preview = extractor.preview(record);
                                    writeQueue.put(new ExtractedRecord(record, content, preview));
                                } catch (FileTooLargeException e) {
                                    skipped.incrementAndGet();
                                    System.err.println(e.getMessage());
                                } catch (SQLException e) {
                                    failed.incrementAndGet();
                                    System.err.println("Failed to read: " + record.path() + " — " + e.getMessage());
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            })
            ).get();
        } catch (Exception e) {
            System.err.println("Error during parallel indexing: " + e.getMessage());
        } finally {
            extractionDone.set(true);
        }

        try {
            writerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}