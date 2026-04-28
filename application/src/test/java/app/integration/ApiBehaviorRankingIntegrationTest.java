package app.integration;

import app.TestUtils;
import app.server.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API-level end-to-end coverage for behavior ranking ordering and insights payload.
 */
class ApiBehaviorRankingIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void searchWithBehaviorSort_boostsOpenedFileAndReturnsInsights(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        Path opened = root.resolve("opened.txt");
        Path untouched = root.resolve("untouched.txt");
        TestUtils.writeTextFile(opened, "alpha token", TestUtils.timeSecondsSinceEpoch(1_700_100_070L));
        TestUtils.writeTextFile(untouched, "alpha token", TestUtils.timeSecondsSinceEpoch(1_700_100_071L));

        String dbPath = tempDir.resolve("api-behavior.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String encodedDb = URLEncoder.encode(dbPath, StandardCharsets.UTF_8);

            HttpRequest warmupSearch = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search?q=alpha&limit=20&db=" + encodedDb))
                    .GET()
                    .build();
            HttpResponse<String> warmupResponse = client.send(warmupSearch, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, warmupResponse.statusCode());

            String openBody = """
                    {
                      "dbPath": "%s",
                      "query": "alpha",
                      "filePath": "%s",
                      "resultPosition": 3
                    }
                    """.formatted(escapeJson(dbPath), escapeJson(opened.toString()));
            HttpRequest openRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search/open"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(openBody))
                    .build();
            HttpResponse<String> openResponse = client.send(openRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(202, openResponse.statusCode());

            HttpRequest behaviorSearch = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/search?q=alpha+sort:behavior&limit=20&db=" + encodedDb))
                    .GET()
                    .build();
            HttpResponse<String> behaviorResponse = client.send(behaviorSearch, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, behaviorResponse.statusCode());

            JsonNode payload = JSON.readTree(behaviorResponse.body());
            assertTrue(payload.isArray() && payload.size() > 0, "Expected non-empty ranked payload");
            JsonNode first = payload.get(0);
            String firstPath = normalizePathText(first.path("result").path("path").asText());
            assertEquals(opened.toAbsolutePath().normalize().toString(), firstPath);
            assertTrue(first.path("insights").isArray(), "Expected insights array in behavior payload");
            assertTrue(first.path("insights").size() > 0, "Expected insights for boosted result");
            assertTrue(
                    first.path("insights").toString().contains("You've opened this"),
                    "Expected open-frequency insight in API payload"
            );
        }
    }

    private static int randomFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String normalizePathText(String pathText) {
        if (pathText != null && pathText.startsWith("file:/")) {
            return Path.of(URI.create(pathText)).toAbsolutePath().normalize().toString();
        }
        return Path.of(pathText).toAbsolutePath().normalize().toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
