package app.integration;

import app.TestUtils;
import app.db.SqliteDatabaseProvider;
import app.service.SearchService;
import app.service.IndexService;
import app.indexer.IndexReport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises resilience paths: DB corruption, unreadable files, and symlink
 * loop environments (best-effort depending on platform capabilities).
 */
class ErrorHandlingTest {

    @Test
    void dbErrorsPropagateToService(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path badDb = tempDir.resolve("bad.db");
        Files.writeString(badDb, "not a sqlite database", StandardCharsets.UTF_8);

        SearchService searchService = new SearchService(new SqliteDatabaseProvider());

        assertThrows(SQLException.class, () -> searchService.search(
                badDb.toString(),
                "alpha",
                10
        ));
    }

    @Test
    void unreadableFilesDoNotCrashIndexing(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path unreadable = root.resolve("unreadable.txt");
        TestUtils.writeTextFile(unreadable, "secretTerm", FileTime.fromMillis(System.currentTimeMillis()));

        try {
            boolean changed = unreadable.toFile().setReadable(false, false);
            if (!changed) {
                Assumptions.assumeTrue(true, "Permissions cannot be changed on this platform; skipping edge assertion.");
            }
        } catch (SecurityException ignored) {
            Assumptions.assumeTrue(true, "Cannot change permissions; skipping edge assertion.");
        }

        IndexService indexService = new IndexService(new SqliteDatabaseProvider());
        IndexReport report = indexService.indexNow(
                tempDir.resolve("permission.db").toString(),
                root,
                List.of(),
                10,
                3,
                50
        );

        assertTrue(report.totalFiles() >= 1);
    }

    @Test
    void symlinkLoopDoesNotCrashCrawl(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path target = root.resolve("target.txt");
        TestUtils.writeTextFile(target, "alpha", FileTime.fromMillis(System.currentTimeMillis()));

        Path link = root.resolve("link-to-root");
        try {
            Files.createSymbolicLink(link, root);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.assumeTrue(false, "Symlink not supported here: " + e.getMessage());
        }

        IndexService indexService = new IndexService(new SqliteDatabaseProvider());
        IndexReport report = indexService.indexNow(
                tempDir.resolve("symlink.db").toString(),
                root,
                List.of(),
                10,
                3,
                50
        );

        assertTrue(report.totalFiles() >= 1);
        assertFalse(TestUtils.search(tempDir.resolve("symlink.db").toString(), "alpha", 10).isEmpty());
    }
}

