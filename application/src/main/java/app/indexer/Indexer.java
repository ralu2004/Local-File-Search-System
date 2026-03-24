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
import java.util.HashSet;
import java.util.Set;

public class Indexer {

    private enum IndexResult { INDEXED, SKIPPED, FAILED }

    private final Crawler crawler;
    private final Extractor extractor;
    private final FileRepository repository;
    private final IndexRunRepository indexRunRepository;

    public Indexer(FileRepository repository, IndexRunRepository indexRunRepository, Crawler crawler, Extractor extractor) {
        this.repository = repository;
        this.indexRunRepository = indexRunRepository;
        this.crawler = crawler;
        this.extractor = extractor;
    }

    public IndexReport run() {
        Instant start = Instant.now();
        long runId = 0;
        try {
            runId = indexRunRepository.startIndexing(LocalDateTime.now());
        } catch (SQLException e) {
            System.err.println("Failed to start index run tracking: " + e.getMessage());
        }

        int totalFiles = 0, indexed = 0, failed = 0, skipped = 0, deleted = 0;
        Set<Path> paths = new HashSet<>();

        for (FileRecord record : (Iterable<FileRecord>) crawler.crawl()::iterator) {
            totalFiles++;
            paths.add(record.path());
            switch (indexFile(record)) {
                case INDEXED -> indexed++;
                case SKIPPED -> skipped++;
                case FAILED  -> failed++;
            }
            if (totalFiles % 100 == 0) {
                System.out.println("Progress: " + totalFiles + " files processed...");
            }
        }

        try {
            deleted = repository.batchDelete(paths);
            repository.optimizeFts();
        } catch (SQLException e) {
            System.err.println("Something went wrong while deleting files: " + e.getMessage());
        }

        Duration elapsed = Duration.between(start, Instant.now());
        IndexReport report = new IndexReport(totalFiles, indexed, skipped, failed, deleted, elapsed);

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
            ExtractedRecord extracted = extractor.extractWithPreview(record);
            repository.upsert(record, extracted.content(), extracted.preview());
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