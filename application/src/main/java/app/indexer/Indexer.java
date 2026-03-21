package app.indexer;

import app.crawler.Crawler;
import app.extractor.Extractor;
import app.extractor.FileTooLargeException;
import app.model.FileRecord;
import app.repository.FileRepository;

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

    public Indexer(FileRepository repository, Crawler crawler, Extractor extractor) {
        this.repository = repository;
        this.crawler = crawler;
        this.extractor = extractor;
    }

    public IndexReport run() {
        int totalFiles = 0, indexed = 0, failed = 0, skipped = 0, deleted = 0;
        Set<Path> paths = new HashSet<>();
        Instant start = Instant.now();

        for (FileRecord record : (Iterable<FileRecord>) crawler.crawl()::iterator) {
            totalFiles++;
            paths.add(record.path());
            switch (indexFile(record)) {
                case INDEXED -> indexed++;
                case SKIPPED -> skipped++;
                case FAILED  -> failed++;
            }
        }

        try {
            deleted = repository.batchDelete(paths);
            repository.optimizeFts();
        } catch (SQLException e) {
            System.err.println("Something went wrong while deleting files: " + e.getMessage());
        }

        Duration elapsed = Duration.between(start, Instant.now());
        return new IndexReport(totalFiles, indexed, skipped, failed, deleted, elapsed);
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