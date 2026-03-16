package app.indexer;

import app.crawler.Crawler;
import app.db.Database;
import app.extractor.Extractor;
import app.model.FileRecord;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

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

        Instant start = Instant.now();

        for (FileRecord record : (Iterable<FileRecord>) crawler.crawl()::iterator) {
            totalFiles++;
            try {
                String content = extractor.extract(record);
                String preview = extractor.preview(record);
                db.upsert(record, content, preview);
                indexed++;
            } catch (SQLException e) {
                failed++;
                System.err.println("Failed to index file: " + record.path() + " — " + e.getMessage());
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        // skipped is 0 for now, will change if more filtering logic is added
        return new IndexReport(totalFiles, indexed, 0, failed, elapsed);
    }
}