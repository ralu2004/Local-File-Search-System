package app.integration;

import app.TestUtils;
import app.server.ApiServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the API endpoint that records opened search results.
 */
class ApiSearchOpenEndpointIntegrationTest {

    @Test
    void postSearchOpen_recordsOpenEvent(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Path target = root.resolve("alpha.txt");
        TestUtils.writeTextFile(target, "alpha content", TestUtils.timeSecondsSinceEpoch(1_700_100_040L));

        String dbPath = tempDir.resolve("api-open.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String body = """
                    {
                      "dbPath": "%s",
                      "query": "Alpha",
                      "filePath": "%s",
                      "resultPosition": 1
                    }
                    """.formatted(escapeJson(dbPath), escapeJson(target.toString()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search/open"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(202, response.statusCode());
        }

        try (Connection conn = DriverManager.getConnection(TestUtils.jdbcUrl(dbPath));
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT normalized_query, file_path, result_position
                     FROM result_open_history
                     ORDER BY id DESC
                     LIMIT 1
                     """);
             ResultSet rs = stmt.executeQuery()) {
            assertTrue(rs.next(), "Expected a row in result_open_history");
            assertEquals("alpha", rs.getString("normalized_query"));
            assertEquals(target.toString(), rs.getString("file_path"));
            assertEquals(1, rs.getInt("result_position"));
        }
    }

    @Test
    void postSearchOpen_withoutFilePath_returnsBadRequest(@TempDir Path tempDir) throws Exception {
        String dbPath = tempDir.resolve("api-open-invalid.db").toString();
        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String body = """
                    {
                      "dbPath": "%s",
                      "query": "Alpha",
                      "resultPosition": 1
                    }
                    """.formatted(escapeJson(dbPath));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search/open"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(400, response.statusCode());
        }
    }

    private static int randomFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
