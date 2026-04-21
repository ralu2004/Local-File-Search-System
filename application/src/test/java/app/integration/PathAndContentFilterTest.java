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
 * End-to-end tests for {@code path:} and {@code content:} search qualifiers.
 */
class PathAndContentFilterTest {

    private static final FileTime T = FileTime.from(Instant.parse("2025-01-01T00:00:00Z"));

    @Test
    void contentFilterMatchesFilesByBodyText(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path match = root.resolve("notes.txt");
        Path noMatch = root.resolve("other.txt");

        TestUtils.writeTextFile(match, "the quick brown fox", T);
        TestUtils.writeTextFile(noMatch, "nothing relevant here", T);

        String dbPath = tempDir.resolve("content-basic.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "content:fox", 20);
        assertTrue(results.stream().anyMatch(r -> r.path().equals(match)),
                "Expected match file in results");
        assertTrue(results.stream().noneMatch(r -> r.path().equals(noMatch)),
                "Expected non-matching file to be excluded");
    }

    @Test
    void contentFilterDoesNotMatchFilenameOnly(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        // filename contains "report" but body does not
        Path filenameOnly = root.resolve("report.txt");
        // body contains "report" but filename does not
        Path bodyOnly = root.resolve("notes.txt");

        TestUtils.writeTextFile(filenameOnly, "nothing here", T);
        TestUtils.writeTextFile(bodyOnly, "this is a report", T);

        String dbPath = tempDir.resolve("content-vs-filename.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "content:report", 20);
        assertTrue(results.stream().anyMatch(r -> r.path().equals(bodyOnly)),
                "Expected body-only match in results");
        assertTrue(results.stream().noneMatch(r -> r.path().equals(filenameOnly)),
                "Expected filename-only file to be excluded from content: search");
    }

    @Test
    void contentFilterCombinedWithFreeTerm(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        // matches both the free term (in filename) and content filter
        Path fullMatch = root.resolve("config.txt");
        // matches content filter but not free term
        Path contentOnly = root.resolve("notes.txt");
        // matches free term (in filename) but not content filter
        Path freeTermOnly = root.resolve("settings.txt");

        TestUtils.writeTextFile(fullMatch, "database configuration value", T);
        TestUtils.writeTextFile(contentOnly, "database configuration value", T);
        TestUtils.writeTextFile(freeTermOnly, "something else entirely", T);

        String dbPath = tempDir.resolve("content-with-free.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        // "config" matches filename of config.txt AND content; "database" is in content
        List<SearchResult> results = TestUtils.search(dbPath, "config content:database", 20);
        assertFalse(results.isEmpty(), "Expected at least one result");
        // freeTermOnly has neither "config" in content nor "database" in content
        assertTrue(results.stream().noneMatch(r -> r.path().equals(freeTermOnly)),
                "File with no content match should be excluded");
    }

    @Test
    void pathFilterMatchesFilesUnderSubdirectory(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Path subDir = root.resolve("projects").resolve("alpha");
        Path otherDir = root.resolve("archive");

        Files.createDirectories(subDir);
        Files.createDirectories(otherDir);

        Path inPath = subDir.resolve("readme.txt");
        Path outOfPath = otherDir.resolve("readme.txt");

        TestUtils.writeTextFile(inPath, "alpha project notes", T);
        TestUtils.writeTextFile(outOfPath, "archived content", T);

        String dbPath = tempDir.resolve("path-subdir.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "path:projects", 20);
        assertTrue(results.stream().anyMatch(r -> r.path().equals(inPath)),
                "File under matching path should appear in results");
        assertTrue(results.stream().noneMatch(r -> r.path().equals(outOfPath)),
                "File outside matching path should be excluded");
    }

    @Test
    void pathFilterCombinedWithContentFilter(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Path docsDir = root.resolve("docs");
        Path srcDir = root.resolve("src");

        Files.createDirectories(docsDir);
        Files.createDirectories(srcDir);

        // right path, right content
        Path target = docsDir.resolve("guide.txt");
        // right content, wrong path
        Path wrongPath = srcDir.resolve("guide.txt");
        // right path, wrong content
        Path wrongContent = docsDir.resolve("other.txt");

        TestUtils.writeTextFile(target, "installation instructions", T);
        TestUtils.writeTextFile(wrongPath, "installation instructions", T);
        TestUtils.writeTextFile(wrongContent, "unrelated material", T);

        String dbPath = tempDir.resolve("path-and-content.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> results = TestUtils.search(dbPath, "path:docs content:installation", 20);
        assertTrue(results.stream().anyMatch(r -> r.path().equals(target)),
                "File matching both path and content should appear");
        assertTrue(results.stream().noneMatch(r -> r.path().equals(wrongPath)),
                "File with wrong path should be excluded");
        assertTrue(results.stream().noneMatch(r -> r.path().equals(wrongContent)),
                "File with wrong content should be excluded");
    }
}