package app.integration;

import app.TestUtils;
import app.db.DatabaseSession;
import app.db.sqlite.SqliteDatabaseProvider;
import app.indexer.IndexReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates database schema bootstrap (create + initialize)
 * and runtime ignore-rule behavior.
 */
class SchemaAndIgnoreTest {

    @Test
    void schemaInitializerCreatesCoreTablesAndIndexes(@TempDir Path tempDir) throws SQLException, IOException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        String dbPath = tempDir.resolve("schema.db").toString();

        try (DatabaseSession db = new SqliteDatabaseProvider().open(dbPath)) {
            assertNotNull(db);
        } catch (IOException e) {
            fail("Unexpected IO error: " + e.getMessage());
        }

        try (Connection conn = DriverManager.getConnection(TestUtils.jdbcUrl(dbPath));
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type IN ('table','index') AND name IN ('files','files_fts','idx_files_extension','idx_files_modified_at')")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean hasFiles = false;
                boolean hasFts = false;
                boolean hasExtIdx = false;
                boolean hasModIdx = false;
                while (rs.next()) {
                    String name = rs.getString("name");
                    hasFiles |= "files".equals(name);
                    hasFts |= "files_fts".equals(name);
                    hasExtIdx |= "idx_files_extension".equals(name);
                    hasModIdx |= "idx_files_modified_at".equals(name);
                }
                assertTrue(hasFiles, "Expected 'files' table to exist");
                assertTrue(hasFts, "Expected 'files_fts' virtual table to exist");
                assertTrue(hasExtIdx, "Expected index on files(extension) to exist");
                assertTrue(hasModIdx, "Expected index on files(modified_at) to exist");
            }
        }
    }

    @Test
    void ignoreRulesAreAppliedRecursively(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Path nested = root.resolve("nested");
        Files.createDirectories(nested);

        long t = 1_700_000_200L;
        Path keep = root.resolve("keep.txt");
        Path skip = nested.resolve("skip.txt");
        TestUtils.writeTextFile(keep, "keepTerm", FileTime.fromMillis(t * 1000));
        TestUtils.writeTextFile(skip, "skipTerm", FileTime.fromMillis((t + 1) * 1000));

        String dbPath = tempDir.resolve("ignore.db").toString();
        IndexReport report = TestUtils.indexDirectory(dbPath, root, List.of("nested"), 10, 3, 50);
        assertTrue(report.totalFiles() >= 1, "Expected at least keep.txt to be crawled");

        assertTrue(TestUtils.search(dbPath, "keepTerm", 10).stream()
                .anyMatch(r -> r.path().equals(keep)), "Expected keep.txt to be indexed");

        assertTrue(TestUtils.search(dbPath, "skipTerm", 10).isEmpty(), "Expected skip.txt to be ignored");
    }
}

