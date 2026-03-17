package app.indexer;

import app.crawler.Crawler;
import app.db.Database;
import app.extractor.Extractor;
import app.model.FileRecord;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class Indexer {

    private final Crawler crawler;
    private final Extractor extractor;
    private final Database db;

    public Indexer(Database db, Crawler crawler, Extractor extractor) {
        this.db = db;
        this.crawler = crawler;
        this.extractor = extractor;
    }

    public IndexReport run() {
        int totalFiles = 0;
        int indexed = 0;
        int failed = 0;
        int skipped = 0;
        int deleted = 0;
        Set<Path> paths = new HashSet<>();

        Instant start = Instant.now();

        for (FileRecord record : (Iterable<FileRecord>) crawler.crawl()::iterator) {
            totalFiles++;
            paths.add(record.path());
            try {
                LocalDateTime storedModifiedAt = db.getModifiedAt(record.path());
                if (storedModifiedAt != null && storedModifiedAt.equals(record.modifiedAt())) {
                    skipped++;
                    continue;
                }

                String content = extractor.extract(record);
                String preview = extractor.preview(record);
                db.upsert(record, content, preview);
                indexed++;
            } catch (SQLException e) {
                failed++;
                System.err.println("Failed to index file: " + record.path() + " — " + e.getMessage());
            }
        }

        try {
            deleted = db.batchDelete(paths);
        } catch (SQLException e) {
            System.err.println("Something went wrong while deleting files: " + e.getMessage());
        }
        Duration elapsed = Duration.between(start, Instant.now());
        return new IndexReport(totalFiles, indexed, skipped, failed, deleted, elapsed);
    }
}