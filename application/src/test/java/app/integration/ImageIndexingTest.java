package app.integration;

import app.TestUtils;
import app.indexer.IndexReport;
import app.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that image files are indexed with dominant color extraction
 * and are searchable via the color: filter.
 */
class ImageIndexingTest {

    private void writeSolidColorImage(Path path, int r, int g, int b) throws IOException {
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

    @Test
    void imageFilesAreIndexedWithDominantColor(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path redImage = root.resolve("red.png");
        writeSolidColorImage(redImage, 220, 30, 30);

        String dbPath = tempDir.resolve("images.db").toString();
        IndexReport report = TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        assertEquals(1, report.totalFiles());
        assertEquals(1, report.indexed());

        String color = TestUtils.dominantColor(dbPath, redImage);
        assertNotNull(color, "Expected dominant color to be stored");
        assertEquals("red", color);
    }

    @Test
    void colorFilterReturnsMatchingImages(@TempDir Path tempDir) throws IOException, SQLException {
        Path root = tempDir.resolve("root");
        Files.createDirectories(root);

        Path redImage   = root.resolve("red.png");
        Path blueImage  = root.resolve("blue.png");
        writeSolidColorImage(redImage,  220, 30, 30);
        writeSolidColorImage(blueImage, 30, 30, 220);

        String dbPath = tempDir.resolve("colorfilter.db").toString();
        TestUtils.indexDirectory(dbPath, root, List.of(), 10, 3, 50);

        List<SearchResult> redResults = TestUtils.search(dbPath, "color:red", 10);
        List<SearchResult> blueResults = TestUtils.search(dbPath, "color:blue", 10);

        assertEquals(1, redResults.size());
        assertEquals(1, blueResults.size());
        assertTrue(redResults.get(0).path().toString().contains("red.png"));
        assertTrue(blueResults.get(0).path().toString().contains("blue.png"));
    }
}