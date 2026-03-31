package app.integration;

import app.TestUtils;
import app.db.SqliteDatabaseProvider;
import app.indexer.job.BackgroundIndexer;
import app.indexer.job.IndexingJobSnapshot;
import app.indexer.job.IndexingJobStatus;
import app.service.IndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that background indexing exposes live progress, reaches a terminal
 * status, and publishes a final report.
 */
class BackgroundIndexingProgressTest {

    @Test
    void backgroundIndexerPublishesProgressAndReturnsReport(@TempDir Path tempDir) throws IOException, SQLException, InterruptedException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        long t = System.currentTimeMillis() / 1000 * 1000;
        for (int i = 0; i < 5; i++) {
            Path f = root.resolve("file" + i + ".txt");
            TestUtils.writeTextFile(f, "alpha " + i, FileTime.fromMillis(t + i * 1000));
        }

        String dbPath = tempDir.resolve("background.db").toString();

        BackgroundIndexer backgroundIndexer = new BackgroundIndexer();
        IndexService indexService = new IndexService(new SqliteDatabaseProvider());
        try {
            boolean started = backgroundIndexer.start(live ->
                    indexService.createBackgroundIndexer(
                            dbPath,
                            root.toString(),
                            List.of(),
                            10,
                            3,
                            50,
                            live
                    )
            );
            assertTrue(started);

            boolean sawRunning = false;
            boolean sawLiveProgress = false;
            for (int i = 0; i < 200; i++) { // up to ~10s
                IndexingJobSnapshot snapshot = backgroundIndexer.getSnapshot();
                if (snapshot.status() == IndexingJobStatus.RUNNING) {
                    sawRunning = true;
                    if (snapshot.liveProgress() != null) {
                        sawLiveProgress = true;
                        break;
                    }
                }
                Thread.sleep(50);
            }
            assertTrue(sawRunning, "Expected background indexing to enter RUNNING state");
            assertTrue(sawLiveProgress, "Expected snapshot to include liveProgress while running");

            IndexingJobSnapshot end;
            for (int i = 0; i < 200; i++) {
                end = backgroundIndexer.getSnapshot();
                if (end.status() == IndexingJobStatus.COMPLETED || end.status() == IndexingJobStatus.FAILED) {
                    break;
                }
                Thread.sleep(50);
            }

            end = backgroundIndexer.getSnapshot();
            assertEquals(IndexingJobStatus.COMPLETED, end.status(), "Expected background job to complete");
            assertNotNull(end.lastReport(), "Expected final IndexReport to be available");
            assertTrue(end.lastReport().totalFiles() >= 5);

            assertFalse(TestUtils.search(dbPath, "alpha", 10).isEmpty());
        } finally {
            backgroundIndexer.close();
        }
    }
}

