package app.integration;

import app.TestUtils;
import app.indexer.IndexReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Producer-Consumer indexing pipeline correctly handles
 * concurrent extraction without losing or corrupting records.
 */
class ParallelIndexingTest {

    @Test
    void allFilesAreIndexedCorrectlyUnderConcurrentExtraction(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        int fileCount = 100;
        long base = 1_700_000_000L;
        for (int i = 0; i < fileCount; i++) {
            Path f = root.resolve("file" + i + ".txt");
            TestUtils.writeTextFile(f, "content of file " + i,
                    FileTime.fromMillis((base + i) * 1000));
        }

        String dbPath = tempDir.resolve("parallel.db").toString();
        IndexReport report = TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 10);

        assertEquals(fileCount, report.totalFiles());
        assertEquals(fileCount, report.indexed());
        assertEquals(0, report.failed());

        List<app.model.SearchResult> results = TestUtils.search(dbPath, "content", 200);
        assertEquals(fileCount, results.size(), "All files should be searchable after concurrent indexing");
    }

    @Test
    void noRecordsAreLostWithSmallBatchSize(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        int fileCount = 50;
        long base = 1_700_001_000L;
        for (int i = 0; i < fileCount; i++) {
            TestUtils.writeTextFile(
                    root.resolve("doc" + i + ".txt"),
                    "document " + i,
                    FileTime.fromMillis((base + i) * 1000));
        }

        String dbPath = tempDir.resolve("smallbatch.db").toString();
        IndexReport report = TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 1);

        assertEquals(fileCount, report.totalFiles());
        assertEquals(fileCount, report.indexed());
        assertEquals(0, report.failed());
    }
}