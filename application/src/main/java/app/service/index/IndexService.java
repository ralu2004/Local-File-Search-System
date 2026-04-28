package app.service.index;

import app.crawler.Crawler;
import app.db.DatabaseProvider;
import app.extractor.Extractor;
import app.indexer.IndexReport;
import app.indexer.Indexer;
import app.indexer.job.IndexingLiveProgress;
import app.repository.CloseableIndexSession;
import app.service.support.DatabaseAccessor;

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

    private final DatabaseAccessor databaseAccessor;

    public IndexService(DatabaseProvider databaseProvider) {
        this.databaseAccessor = new DatabaseAccessor(databaseProvider);
    }

    public IndexReport indexNow(String dbPath, Path root, List<String> ignoreRules,
                                int maxFileSizeMb, int previewLines, int batchSize)
            throws SQLException, IOException {
        try (CloseableIndexSession session = databaseAccessor.openIndexSession(dbPath)) {
            return createIndexer(session, root, ignoreRules, maxFileSizeMb, previewLines, batchSize, null).run();
        }
    }

    public Indexer createBackgroundIndexer(String dbPath, String root, List<String> ignoreRules,
                                           int maxFileSizeMb, int previewLines, int batchSize,
                                           IndexingLiveProgress liveProgress) {
        try {
            CloseableIndexSession session = databaseAccessor.openIndexSession(dbPath);
            Crawler crawler = new Crawler(Path.of(root), safeIgnoreRules(ignoreRules));
            Extractor extractor = new Extractor(
                    sanitizePreviewLines(previewLines),
                    toMaxBytes(maxFileSizeMb)
            );
            return new Indexer(session, session, session, crawler, extractor, sanitizeBatchSize(batchSize), liveProgress) {
                @Override
                public IndexReport run() {
                    try (session) {
                        return super.run();
                    } catch (SQLException e) {
                        throw new IllegalStateException("Failed to close index session: " + e.getMessage(), e);
                    }
                }
            };
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Cannot start indexer: " + e.getMessage(), e);
        }
    }

    private Indexer createIndexer(CloseableIndexSession session, Path root, List<String> ignoreRules,
                                  int maxFileSizeMb, int previewLines, int batchSize,
                                  IndexingLiveProgress liveProgress) {
        Crawler crawler = new Crawler(root, safeIgnoreRules(ignoreRules));
        Extractor extractor = new Extractor(
                sanitizePreviewLines(previewLines),
                toMaxBytes(maxFileSizeMb)
        );
        return new Indexer(session, session, session, crawler, extractor, sanitizeBatchSize(batchSize), liveProgress);
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
}
