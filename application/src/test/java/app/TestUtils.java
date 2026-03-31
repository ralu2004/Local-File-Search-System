package app;

import app.db.DatabaseProvider;
import app.db.SqliteDatabaseProvider;
import app.indexer.IndexReport;
import app.model.SearchResult;
import app.service.IndexService;
import app.service.SearchService;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * Shared helpers for integration tests that need filesystem setup and
 * direct DB assertions.
 */
public final class TestUtils {
    private TestUtils() {}

    public static void writeTextFile(Path path, String content, FileTime lastModifiedAt) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        if (lastModifiedAt != null) {
            Files.setLastModifiedTime(path, lastModifiedAt);
        }
    }

    public static FileTime timeSecondsSinceEpoch(long seconds) {
        return FileTime.from(Instant.ofEpochSecond(seconds));
    }

    public static String jdbcUrl(String dbPath) {
        return "jdbc:sqlite:" + dbPath;
    }

    public static String indexedAt(String dbPath, Path filePath) throws SQLException {
        String sql = "SELECT indexed_at FROM files WHERE path = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl(dbPath));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("indexed_at");
            }
        }
    }

    public static long filesCount(String dbPath, Path filePath) throws SQLException {
        String sql = "SELECT COUNT(*) AS c FROM files WHERE path = ?";
        try (Connection conn = DriverManager.getConnection(jdbcUrl(dbPath));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                return rs.getLong("c");
            }
        }
    }

    public static IndexReport indexDirectory(String dbPath, Path root, List<String> ignoreRules,
                                              int maxFileSizeMb, int previewLines, int batchSize)
            throws SQLException, IOException {
        DatabaseProvider provider = new SqliteDatabaseProvider();
        IndexService indexService = new IndexService(provider);
        return indexService.indexNow(dbPath, root, ignoreRules, maxFileSizeMb, previewLines, batchSize);
    }

    public static List<SearchResult> search(String dbPath, String query, int limit) throws SQLException, IOException {
        DatabaseProvider provider = new SqliteDatabaseProvider();
        SearchService searchService = new SearchService(provider);
        return searchService.search(dbPath, query, limit);
    }

    public static void assertResultContainsPath(List<SearchResult> results, Path expectedPath) {
        Assertions.assertTrue(
                results.stream().anyMatch(r -> r.path().equals(expectedPath)),
                "Expected result to contain path: " + expectedPath
        );
    }
}

