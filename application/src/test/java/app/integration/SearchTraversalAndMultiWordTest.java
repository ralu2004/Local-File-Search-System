package app.integration;

import app.TestUtils;
import app.indexer.IndexReport;
import app.model.SearchResult;
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
 * Covers core indexing/search behavior: recursive traversal, single-word
 * search, multi-word full-text matching, and preview availability.
 */
class SearchTraversalAndMultiWordTest {

    @Test
    void recursiveTraversalAndSingleWordSearchWork(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Path nested = root.resolve("nested");
        Files.createDirectories(nested);

        Path a = nested.resolve("a.txt");
        Path b = root.resolve("b.txt");

        long baseSeconds = 1_700_000_000L;
        TestUtils.writeTextFile(a, "alpha\nsecond line", FileTime.fromMillis(baseSeconds * 1000));
        TestUtils.writeTextFile(b, "beta only", FileTime.fromMillis((baseSeconds + 1) * 1000));

        String dbPath = tempDir.resolve("test-search.db").toString();
        IndexReport report = TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);
        assertTrue(report.totalFiles() >= 2);

        List<SearchResult> alphaResults = TestUtils.search(dbPath, "alpha", 20);
        TestUtils.assertResultContainsPath(alphaResults, a);
        assertTrue(alphaResults.stream().anyMatch(r -> r.preview().contains("alpha")));

        List<SearchResult> betaResults = TestUtils.search(dbPath, "beta", 20);
        TestUtils.assertResultContainsPath(betaResults, b);
    }

    @Test
    void multiWordSearchUsesFullTextMatching(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Path nested = root.resolve("nested2");
        Files.createDirectories(nested);

        Path phrase = nested.resolve("phrase.txt");
        Path helloOnly = nested.resolve("hello.txt");

        long baseSeconds = 1_700_000_100L;
        TestUtils.writeTextFile(phrase, "hello world\nanother line", FileTime.fromMillis(baseSeconds * 1000));
        TestUtils.writeTextFile(helloOnly, "hello only", FileTime.fromMillis((baseSeconds + 1) * 1000));

        String dbPath = tempDir.resolve("test-multisearch.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "hello world", 20);
        assertEquals(1, results.size(), "Only the file containing both terms should match.");
        assertEquals(phrase, results.getFirst().path());
        assertTrue(results.getFirst().preview().contains("hello"));
    }
}

