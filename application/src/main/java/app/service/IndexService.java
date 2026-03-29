package app.service;

import app.crawler.Crawler;
import app.db.Database;
import app.db.DatabaseProvider;
import app.extractor.Extractor;
import app.indexer.IndexReport;
import app.indexer.Indexer;
import app.indexer.job.IndexingLiveProgress;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

/**
 * Application service for indexing use cases.
 */
public class IndexService {
    
    private static final int DEFAULT_MAX_FILE_SIZE_MB = 10;
    private static final int DEFAULT_PREVIEW_LINES = 3;
    private static final int DEFAULT_BATCH_SIZE = 250;

    private final DatabaseProvider databaseProvider;

    public IndexService(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    public IndexReport indexNow(String dbPath, Path root, List<String> ignoreRules,
                                int maxFileSizeMb, int previewLines, int batchSize)
            throws SQLException, IOException {
        try (Database db = openDatabase(dbPath)) {
            return createIndexer(db, root, ignoreRules, maxFileSizeMb, previewLines, batchSize, null).run();
        }
    }

    public Indexer createBackgroundIndexer(String dbPath, String root, List<String> ignoreRules,
                                           int maxFileSizeMb, int previewLines, int batchSize,
                                           IndexingLiveProgress liveProgress) {
        try {
            Database db = openDatabase(dbPath);
            Crawler crawler = new Crawler(Path.of(root), safeIgnoreRules(ignoreRules));
            Extractor extractor = new Extractor(
                    sanitizePreviewLines(previewLines),
                    toMaxBytes(maxFileSizeMb)
            );
            return new Indexer(db, db, db, crawler, extractor, sanitizeBatchSize(batchSize), liveProgress) {
                @Override
                public IndexReport run() {
                    try (db) {
                        return super.run();
                    }
                }
            };
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Cannot start indexer: " + e.getMessage(), e);
        }
    }

    private Indexer createIndexer(Database db, Path root, List<String> ignoreRules,
                                  int maxFileSizeMb, int previewLines, int batchSize,
                                  IndexingLiveProgress liveProgress) {
        Crawler crawler = new Crawler(root, safeIgnoreRules(ignoreRules));
        Extractor extractor = new Extractor(
                sanitizePreviewLines(previewLines),
                toMaxBytes(maxFileSizeMb)
        );
        return new Indexer(db, db, db, crawler, extractor, sanitizeBatchSize(batchSize), liveProgress);
    }

    private long toMaxBytes(int maxFileSizeMb) {
        return (long) sanitizeMaxFileSizeMb(maxFileSizeMb) * 1024 * 1024;
    }

    private int sanitizeMaxFileSizeMb(int value) {
        return value > 0 ? value : DEFAULT_MAX_FILE_SIZE_MB;
    }

    private int sanitizePreviewLines(int value) {
        return value > 0 ? value : DEFAULT_PREVIEW_LINES;
    }

    private int sanitizeBatchSize(int value) {
        return value > 0 ? value : DEFAULT_BATCH_SIZE;
    }

    private List<String> safeIgnoreRules(List<String> ignoreRules) {
        return ignoreRules == null ? List.of() : ignoreRules;
    }

    private Database openDatabase(String dbPath) throws SQLException, IOException {
        if (dbPath == null || dbPath.isBlank()) {
            return databaseProvider.openDefault();
        }
        return databaseProvider.open(dbPath);
    }
}
