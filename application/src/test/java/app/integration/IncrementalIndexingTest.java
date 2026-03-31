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
 * Proves incremental indexing semantics: unchanged files are skipped, modified
 * files are refreshed, and removed files are deleted from the index.
 */
class IncrementalIndexingTest {

    @Test
    void incrementalIndexingSkipsUnchangedAndUpdatesModified(@TempDir Path tempDir) throws IOException, SQLException, InterruptedException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path file1 = root.resolve("file1.txt");
        Path file2 = root.resolve("file2.txt");

        long baseSeconds = 1_700_000_300L;
        TestUtils.writeTextFile(file1, "alpha", FileTime.fromMillis(baseSeconds * 1000));
        TestUtils.writeTextFile(file2, "beta", FileTime.fromMillis((baseSeconds + 1) * 1000));

        String dbPath = tempDir.resolve("incremental.db").toString();
        IndexReport first = TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);
        assertEquals(2, first.totalFiles());

        String indexedAt1Before = TestUtils.indexedAt(dbPath, file1);
        String indexedAt2Before = TestUtils.indexedAt(dbPath, file2);
        assertNotNull(indexedAt1Before);
        assertNotNull(indexedAt2Before);

        Thread.sleep(1100);

        // modify file1 (content + lastModifiedTime)
        TestUtils.writeTextFile(file1, "alpha\nchanged", FileTime.fromMillis((baseSeconds + 5) * 1000));
        IndexReport second = TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);
        assertEquals(2, second.totalFiles());

        String indexedAt1After = TestUtils.indexedAt(dbPath, file1);
        String indexedAt2After = TestUtils.indexedAt(dbPath, file2);

        assertNotNull(indexedAt1After);
        assertNotNull(indexedAt2After);

        assertNotEquals(indexedAt1Before, indexedAt1After, "file1 should be updated (indexed_at changed)");
        assertEquals(indexedAt2Before, indexedAt2After, "file2 should be skipped (indexed_at unchanged)");

        assertEquals(1, TestUtils.search(dbPath, "changed", 10).size());
        assertTrue(TestUtils.search(dbPath, "beta", 10).stream().anyMatch(r -> r.path().equals(file2)));
    }

    @Test
    void incrementalIndexingDeletesRemovedRecords(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path file1 = root.resolve("file1.txt");
        Path file2 = root.resolve("file2.txt");

        long baseSeconds = 1_700_000_400L;
        TestUtils.writeTextFile(file1, "alpha", FileTime.fromMillis(baseSeconds * 1000));
        TestUtils.writeTextFile(file2, "beta", FileTime.fromMillis((baseSeconds + 1) * 1000));

        String dbPath = tempDir.resolve("incremental-delete.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        assertEquals(1, TestUtils.filesCount(dbPath, file1));
        assertEquals(1, TestUtils.filesCount(dbPath, file2));

        Files.delete(file2);
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        assertEquals(1, TestUtils.filesCount(dbPath, file1));
        assertEquals(0, TestUtils.filesCount(dbPath, file2));
    }
}

