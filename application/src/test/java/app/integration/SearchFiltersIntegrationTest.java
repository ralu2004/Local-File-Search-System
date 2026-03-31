package app.integration;

import app.TestUtils;
import app.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end search filter tests: extension, modified date, size (bytes/units),
 * and mixed full-text + metadata queries.
 */
class SearchFiltersIntegrationTest {

    @Test
    void extensionFilter_returnsOnlyMatchingExtension(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        long t = 1_700_001_000L;
        Path javaFile = root.resolve("Main.java");
        Path txtFile = root.resolve("notes.txt");

        TestUtils.writeTextFile(javaFile, "class Main {}", FileTime.from(Instant.ofEpochSecond(t)));
        TestUtils.writeTextFile(txtFile, "plain note", FileTime.from(Instant.ofEpochSecond(t + 1)));

        String dbPath = tempDir.resolve("filters-ext.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "ext:java", 20);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(r -> "java".equalsIgnoreCase(r.extension())));
        assertTrue(results.stream().anyMatch(r -> r.path().equals(javaFile)));
        assertTrue(results.stream().noneMatch(r -> r.path().equals(txtFile)));
    }

    @Test
    void modifiedFilter_appliesLowerBound(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path oldFile = root.resolve("old.txt");
        Path newFile = root.resolve("new.txt");

        // created_at/modified_at are read from file attributes during crawl
        TestUtils.writeTextFile(oldFile, "old data", FileTime.from(Instant.parse("2024-01-01T00:00:00Z")));
        TestUtils.writeTextFile(newFile, "new data", FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));

        String dbPath = tempDir.resolve("filters-modified.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "modified:2024-06-01", 20);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.path().equals(newFile)));
        assertTrue(results.stream().noneMatch(r -> r.path().equals(oldFile)));
    }

    @Test
    void sizeFilter_supportsBytesAndUnits(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path small = root.resolve("small.txt");
        Path medium = root.resolve("medium.txt");

        TestUtils.writeTextFile(small, "x".repeat(300), FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));
        TestUtils.writeTextFile(medium, "y".repeat(2_500), FileTime.from(Instant.parse("2025-01-01T00:00:01Z")));

        String dbPath = tempDir.resolve("filters-size.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> bytesResults = TestUtils.search(dbPath, "size:1000", 20);
        assertTrue(bytesResults.stream().anyMatch(r -> r.path().equals(medium)));
        assertTrue(bytesResults.stream().noneMatch(r -> r.path().equals(small)));

        List<SearchResult> kbResults = TestUtils.search(dbPath, "size:1kb", 20);
        assertTrue(kbResults.stream().anyMatch(r -> r.path().equals(medium)));
        assertTrue(kbResults.stream().noneMatch(r -> r.path().equals(small)));
    }

    @Test
    void mixedQuery_combinesFullTextAndMetadataFilters(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path jsonMatch = root.resolve("config.json");
        Path txtMatchText = root.resolve("config.txt");
        Path jsonNoText = root.resolve("other.json");

        TestUtils.writeTextFile(jsonMatch, "service config value", FileTime.from(Instant.parse("2025-01-01T00:00:00Z")));
        TestUtils.writeTextFile(txtMatchText, "service config value", FileTime.from(Instant.parse("2025-01-01T00:00:01Z")));
        TestUtils.writeTextFile(jsonNoText, "different content", FileTime.from(Instant.parse("2025-01-01T00:00:02Z")));

        String dbPath = tempDir.resolve("filters-mixed.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "config ext:json", 20);
        assertTrue(results.stream().anyMatch(r -> r.path().equals(jsonMatch)));
        assertTrue(results.stream().noneMatch(r -> r.path().equals(txtMatchText)));
        assertTrue(results.stream().noneMatch(r -> r.path().equals(jsonNoText)));
    }

    @Test
    void maxFileSizeMb_runtimeOptionSkipsTooLargeFiles(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path small = root.resolve("small.txt");
        Path large = root.resolve("large.txt");

        TestUtils.writeTextFile(small, "small file", FileTime.from(Instant.parse("2025-01-02T00:00:00Z")));
        TestUtils.writeTextFile(large, "x".repeat(1_200_000), FileTime.from(Instant.parse("2025-01-02T00:00:01Z")));

        String dbPath = tempDir.resolve("runtime-max-size.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 1, 3, 50);

        assertTrue(TestUtils.search(dbPath, "small file", 20).stream().anyMatch(r -> r.path().equals(small)));
        assertTrue(TestUtils.search(dbPath, "x", 20).stream().noneMatch(r -> r.path().equals(large)));
    }

    @Test
    void previewLines_runtimeOptionAffectsStoredPreview(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path file = root.resolve("preview.txt");
        String content = String.join(System.lineSeparator(), List.of("line1", "line2", "line3", "line4"));
        TestUtils.writeTextFile(file, content, FileTime.from(Instant.parse("2025-01-03T00:00:00Z")));

        String dbPathOneLine = tempDir.resolve("runtime-preview-1.db").toString();
        TestUtils.indexDirectory(dbPathOneLine, root, List.of(), 10, 1, 50);
        SearchResult oneLine = TestUtils.search(dbPathOneLine, "ext:txt", 10).stream()
                .filter(r -> r.path().equals(file))
                .findFirst()
                .orElseThrow();
        assertEquals("line1", oneLine.preview());

        String dbPathThreeLines = tempDir.resolve("runtime-preview-3.db").toString();
        TestUtils.indexDirectory(dbPathThreeLines, root, List.of(), 10, 3, 50);
        SearchResult threeLines = TestUtils.search(dbPathThreeLines, "ext:txt", 10).stream()
                .filter(r -> r.path().equals(file))
                .findFirst()
                .orElseThrow();
        assertEquals(String.join(System.lineSeparator(), List.of("line1", "line2", "line3")), threeLines.preview());
    }

    @Test
    void batchSize_runtimeOptionStillIndexesAllFiles(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        for (int i = 0; i < 7; i++) {
            Path file = root.resolve("f" + i + ".txt");
            TestUtils.writeTextFile(file, "token-" + i, FileTime.from(Instant.ofEpochSecond(1_700_001_500L + i)));
        }

        String dbPath = tempDir.resolve("runtime-batch-size.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 1);

        for (int i = 0; i < 7; i++) {
            String token = "token-" + i;
            String expectedFilename = "f" + i + ".txt";
            List<SearchResult> results = TestUtils.search(dbPath, token, 10);
            assertTrue(results.stream().anyMatch(r -> r.filename().equals(expectedFilename)));
        }
    }
}

