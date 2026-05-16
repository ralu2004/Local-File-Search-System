package app.extractor.strategy;

import app.indexer.PathFeatureExtractor;
import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.search.ranking.PathFeatures;
import app.util.FileTypes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * {@link FileProcessingStrategy} for image files.
 * <p>
 * Extracts the dominant color by sampling pixels across the image
 * and mapping the average RGB value to a named color bucket.
 * The color name is stored as the file content for {@code color:} queries.
 */
public class ImageFileStrategy implements FileProcessingStrategy {

    private static final int SAMPLE_STEP = 10;

    private final PathFeatureExtractor pathFeatureExtractor;

    /**
     * @param pathFeatureExtractor extracts ranking signals from the file path
     */
    public ImageFileStrategy(PathFeatureExtractor pathFeatureExtractor) {
        this.pathFeatureExtractor = pathFeatureExtractor;
    }

    @Override
    public boolean supports(FileRecord record) {
        return FileTypes.IMAGE_EXTENSIONS.contains(record.extension().toLowerCase());
    }

    @Override
    public ExtractedRecord process(FileRecord record) throws IOException {
        BufferedImage image = ImageIO.read(record.path().toFile());
        String dominantColor = image == null ? "unknown" : extractDominantColor(image);
        PathFeatures features = pathFeatureExtractor.extract(record.path());
        return new ExtractedRecord(record, dominantColor, dominantColor, features, dominantColor);
    }

    /**
     * Samples pixels at regular intervals and maps the average RGB
     * to a named color using HSB hue bucketing.
     */
    private String extractDominantColor(BufferedImage image) {
        long r = 0, g = 0, b = 0, count = 0;
        for (int y = 0; y < image.getHeight(); y += SAMPLE_STEP) {
            for (int x = 0; x < image.getWidth(); x += SAMPLE_STEP) {
                int rgb = image.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return "unknown";
        }
        return toColorName((int) (r / count), (int) (g / count), (int) (b / count));
    }

    /**
     * Maps averaged RGB values to a named color using HSB hue ranges.
     */
    private String toColorName(int r, int g, int b) {
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] * 360;
        float saturation = hsb[1];
        float brightness = hsb[2];

        if (brightness < 0.2f) {
            return "black";
        }
        if (brightness > 0.8f && saturation < 0.2f) {
            return "white";
        }
        if (saturation < 0.2f) {
            return "gray";
        }
        if (hue < 30)   { return "red"; }
        if (hue < 60)   { return "orange"; }
        if (hue < 90)   { return "yellow"; }
        if (hue < 150)  { return "green"; }
        if (hue < 210)  { return "cyan"; }
        if (hue < 270)  { return "blue"; }
        if (hue < 330)  { return "purple"; }
        return "red";
    }
}