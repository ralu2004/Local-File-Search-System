package app.integration;

import app.TestUtils;
import app.db.DatabaseProvider;
import app.db.sqlite.SqliteDatabaseProvider;
import app.model.RankedSearchResult;
import app.service.search.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for search activity persistence and history-based
 * suggestion/history retrieval.
 */
class SearchActivityIntegrationTest {

    @Test
    void search_recordsQueryActivity(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("alpha.txt"), "alpha content", TestUtils.timeSecondsSinceEpoch(1_700_100_000L));
        TestUtils.writeTextFile(root.resolve("beta.txt"), "beta content", TestUtils.timeSecondsSinceEpoch(1_700_100_001L));

        String dbPath = tempDir.resolve("search-activity.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        DatabaseProvider provider = new SqliteDatabaseProvider();
        SearchService service = new SearchService(provider);
        service.search(dbPath, "alpha", 20);

        try (Connection conn = DriverManager.getConnection(TestUtils.jdbcUrl(dbPath));
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT query_text, normalized_query, result_count, duration_ms
                     FROM search_history
                     ORDER BY id DESC
                     LIMIT 1
                     """);
             ResultSet rs = stmt.executeQuery()) {
            assertTrue(rs.next(), "Expected at least one search_history row");
            assertEquals("alpha", rs.getString("query_text"));
            assertEquals("alpha", rs.getString("normalized_query"));
            assertTrue(rs.getInt("result_count") >= 1, "Expected at least one matching result");
            assertTrue(rs.getLong("duration_ms") >= 0, "Duration should be non-negative");
        }
    }

    @Test
    void suggestQueries_returnsPrefixMatchesOrderedByFrequency(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("seed.txt"), "seed", TestUtils.timeSecondsSinceEpoch(1_700_100_010L));

        String dbPath = tempDir.resolve("suggestions.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T10:00:00");
        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T10:01:00");
        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T10:02:00");
        insertSearchHistory(dbPath, "docs", "docs", "2026-01-01T10:03:00");
        insertSearchHistory(dbPath, "docs", "docs", "2026-01-01T10:04:00");
        insertSearchHistory(dbPath, "download", "download", "2026-01-01T10:05:00");

        SearchService service = new SearchService(new SqliteDatabaseProvider());
        List<String> suggestions = service.suggestQueries(dbPath, "Do", 10);

        assertFalse(suggestions.isEmpty(), "Expected suggestions for prefix 'Do'");
        assertEquals("docker", suggestions.getFirst(), "Most frequent prefix match should come first");
        assertTrue(suggestions.contains("docs"));
        assertTrue(suggestions.contains("download"));
    }

    @Test
    void recentQueries_returnsUniqueQueriesByLatestUsage(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("seed.txt"), "seed", TestUtils.timeSecondsSinceEpoch(1_700_100_020L));

        String dbPath = tempDir.resolve("recent-queries.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        insertSearchHistory(dbPath, "java", "java", "2026-01-01T09:00:00");
        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T09:05:00");
        insertSearchHistory(dbPath, "java", "java", "2026-01-01T09:10:00");
        insertSearchHistory(dbPath, "docs", "docs", "2026-01-01T09:15:00");

        SearchService service = new SearchService(new SqliteDatabaseProvider());
        List<String> recent = service.recentQueries(dbPath, 10);

        assertEquals(List.of("docs", "java", "docker"), recent);
    }

    @Test
    void recordResultOpen_persistsNormalizedQueryAndPosition(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Path target = root.resolve("alpha.txt");
        TestUtils.writeTextFile(target, "alpha content", TestUtils.timeSecondsSinceEpoch(1_700_100_030L));

        String dbPath = tempDir.resolve("open-events.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        SearchService service = new SearchService(new SqliteDatabaseProvider());
        service.recordResultOpen(dbPath, "  Alpha  ", target.toString(), 2);

        try (Connection conn = DriverManager.getConnection(TestUtils.jdbcUrl(dbPath));
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT query_text, normalized_query, file_path, result_position, opened_at
                     FROM result_open_history
                     ORDER BY id DESC
                     LIMIT 1
                     """);
             ResultSet rs = stmt.executeQuery()) {
            assertTrue(rs.next(), "Expected at least one result_open_history row");
            assertEquals("  Alpha  ", rs.getString("query_text"));
            assertEquals("alpha", rs.getString("normalized_query"));
            assertEquals(target.toString(), rs.getString("file_path"));
            assertEquals(2, rs.getInt("result_position"));
            assertFalse(rs.getString("opened_at").isBlank(), "Expected opened_at timestamp to be persisted");
        }
    }

    @Test
    void behaviorRanking_elevatesOpenedResultsAndReturnsInsights(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Path opened = root.resolve("opened.txt");
        Path untouched = root.resolve("untouched.txt");
        TestUtils.writeTextFile(opened, "alpha token", TestUtils.timeSecondsSinceEpoch(1_700_100_040L));
        TestUtils.writeTextFile(untouched, "alpha token", TestUtils.timeSecondsSinceEpoch(1_700_100_041L));

        String dbPath = tempDir.resolve("behavior-e2e.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        SearchService service = new SearchService(new SqliteDatabaseProvider());
        service.search(dbPath, "alpha", 20);
        service.recordResultOpen(dbPath, "alpha", opened.toString(), 3);

        List<RankedSearchResult> ranked = service.search(dbPath, "alpha sort:behavior", 20);

        assertFalse(ranked.isEmpty(), "Expected behavior-ranked results");
        assertEquals(opened, ranked.getFirst().result().path(), "Opened file should be boosted to top");
        assertTrue(
                ranked.getFirst().insights().stream().anyMatch(i -> i.startsWith("You've opened this")),
                "Expected behavior insights on boosted result"
        );
    }

    private static void insertSearchHistory(String dbPath, String query, String normalized, String executedAt) throws SQLException {
        String sql = """
                INSERT INTO search_history (query_text, normalized_query, result_count, duration_ms, executed_at)
                VALUES (?, ?, 1, 1, ?)
                """;
        try (Connection conn = DriverManager.getConnection(TestUtils.jdbcUrl(dbPath));
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, query);
            stmt.setString(2, normalized);
            stmt.setString(3, executedAt);
            stmt.executeUpdate();
        }
    }
}
