package app.integration;

import app.TestUtils;
import app.server.ApiServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
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
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that context-aware widgets are correctly activated based on
 * search result set composition and query content.
 */
class WidgetActivationIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private static int randomFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void writeSolidColorImage(Path path, int r, int g, int b) throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        int rgb = new java.awt.Color(r, g, b).getRGB();
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                img.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(img, "png", path.toFile());
        Files.setLastModifiedTime(path, FileTime.fromMillis(1_700_000_000L * 1000));
    }

    private static JsonNode searchWidgets(HttpClient client, int port, String query, String dbPath) throws Exception {
        String encodedDb = URLEncoder.encode(dbPath, StandardCharsets.UTF_8);
        String encodedQ = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/search?q=" + encodedQ + "&limit=50&db=" + encodedDb))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body()).path("widgets");
    }

    private static boolean hasWidget(JsonNode widgets, String id) {
        return StreamSupport.stream(widgets.spliterator(), false)
                .anyMatch(w -> id.equals(w.path("id").asText()));
    }

    @Test
    void imageResultsActivateGalleryWidget(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        writeSolidColorImage(root.resolve("red1.png"), 220, 30, 30);
        writeSolidColorImage(root.resolve("red2.png"), 200, 40, 40);
        writeSolidColorImage(root.resolve("red3.png"), 210, 35, 35);

        String dbPath = tempDir.resolve("widgets-gallery.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            JsonNode widgets = searchWidgets(HttpClient.newHttpClient(), port, "color:red", dbPath);
            assertTrue(hasWidget(widgets, "gallery"), "Expected gallery widget for image-heavy results");
        }
    }

    @Test
    void exportWidgetAlwaysPresentWhenResultsExist(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("file.txt"), "hello world",
                FileTime.fromMillis(1_700_000_000L * 1000));

        String dbPath = tempDir.resolve("widgets-export.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            JsonNode widgets = searchWidgets(HttpClient.newHttpClient(), port, "hello", dbPath);
            assertTrue(hasWidget(widgets, "export-list"), "Expected export-list widget for any non-empty results");
        }
    }

    @Test
    void sameDirResultsActivateFolderWidget(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("a.txt"), "shared term",
                FileTime.fromMillis(1_700_000_000L * 1000));
        TestUtils.writeTextFile(root.resolve("b.txt"), "shared term",
                FileTime.fromMillis(1_700_000_001L * 1000));

        String dbPath = tempDir.resolve("widgets-folder.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            JsonNode widgets = searchWidgets(HttpClient.newHttpClient(), port, "shared", dbPath);
            assertTrue(hasWidget(widgets, "open-folder"), "Expected open-folder widget when all results in same directory");
        }
    }

    @Test
    void contentQualifierActivatesContentMarker(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);
        TestUtils.writeTextFile(root.resolve("file.txt"), "unique content here",
                FileTime.fromMillis(1_700_000_000L * 1000));

        String dbPath = tempDir.resolve("widgets-content.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        int port = randomFreePort();
        try (ApiServer server = new ApiServer(port)) {
            server.start();
            JsonNode widgets = searchWidgets(HttpClient.newHttpClient(), port, "content:unique", dbPath);
            assertTrue(hasWidget(widgets, "search-content"), "Expected content marker when query uses content: qualifier");
        }
    }
}