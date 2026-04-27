package app.integration;

import app.TestUtils;
import app.server.ApiServer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for query suggestion and recent-history API endpoints.
 */
class ApiSearchHistoryAndSuggestIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void getSearchSuggest_returnsPrefixMatchesOrderedByFrequency(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("seed.txt"), "seed", TestUtils.timeSecondsSinceEpoch(1_700_100_050L));

        String dbPath = tempDir.resolve("api-suggest.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T10:00:00");
        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T10:01:00");
        insertSearchHistory(dbPath, "docs", "docs", "2026-01-01T10:02:00");
        insertSearchHistory(dbPath, "download", "download", "2026-01-01T10:03:00");

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String encodedDb = URLEncoder.encode(dbPath, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search/suggest?q=do&limit=10&db=" + encodedDb))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            List<String> suggestions = JSON.readValue(response.body(), new TypeReference<>() {});
            assertEquals("docker", suggestions.getFirst(), "Most frequent prefix match should be first");
            assertTrue(suggestions.contains("docs"));
            assertTrue(suggestions.contains("download"));
        }
    }

    @Test
    void getSearchHistory_returnsRecentQueriesByLatestUsage(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("seed.txt"), "seed", TestUtils.timeSecondsSinceEpoch(1_700_100_060L));

        String dbPath = tempDir.resolve("api-history.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        insertSearchHistory(dbPath, "java", "java", "2026-01-01T09:00:00");
        insertSearchHistory(dbPath, "docker", "docker", "2026-01-01T09:05:00");
        insertSearchHistory(dbPath, "java", "java", "2026-01-01T09:10:00");
        insertSearchHistory(dbPath, "docs", "docs", "2026-01-01T09:15:00");

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String encodedDb = URLEncoder.encode(dbPath, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search/history?limit=10&db=" + encodedDb))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());

            List<String> recent = JSON.readValue(response.body(), new TypeReference<>() {});
            assertEquals(List.of("docs", "java", "docker"), recent);
        }
    }

    private static int randomFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
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
