package app.extractor;

import app.indexer.PathFeatureExtractor;
import app.model.FileRecord;
import app.model.ExtractedRecord;
import app.search.ranking.PathFeatures;

import java.io.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads textual file content and derives a preview snippet for indexing.
 * <p>
 * Also extracts {@link PathFeatures} via {@link PathFeatureExtractor} so that
 * ranking signals are computed and stored alongside content at index time.
 */
public class Extractor {

    private static final int DEFAULT_PREVIEW_LINES = 3;
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final int previewLines;
    private final long maxFileSize;
    private final PathFeatureExtractor pathFeatureExtractor;

    public Extractor() {
        this(DEFAULT_PREVIEW_LINES, DEFAULT_MAX_FILE_SIZE);
    }

    public Extractor(int previewLines, long maxFileSize) {
        this(previewLines, maxFileSize, new PathFeatureExtractor());
    }

    public Extractor(int previewLines, long maxFileSize, PathFeatureExtractor pathFeatureExtractor) {
        this.previewLines = previewLines;
        this.maxFileSize = maxFileSize;
        this.pathFeatureExtractor = pathFeatureExtractor;
    }

    public String extract(FileRecord record) {
        return String.join(System.lineSeparator(), readLines(record, Integer.MAX_VALUE));
    }

    public String preview(FileRecord record) {
        return String.join(System.lineSeparator(), readLines(record, previewLines));
    }

    public ExtractedRecord extractWithPreview(FileRecord record) {
        List<String> lines = readLines(record, Integer.MAX_VALUE);
        String content = String.join(System.lineSeparator(), lines);

        int previewLimit = Math.min(previewLines, lines.size());
        String preview = String.join(System.lineSeparator(), lines.subList(0, previewLimit));
        PathFeatures features = pathFeatureExtractor.extract(record.path());
        return new ExtractedRecord(record, content, preview, features);
    }

    private List<String> readLines(FileRecord record, int maxLines) {
        if (isTooLarge(record)) {
            throw new FileTooLargeException(record.path(), record.sizeBytes(), maxFileSize);
        }

        List<String> lines = new ArrayList<>();

        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(record.path().toFile()), decoder))) {
            String line;
            while ((line = br.readLine()) != null && lines.size() < maxLines) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Could not read file: " + record.path());
        }
        return lines;
    }

    private boolean isTooLarge(FileRecord record) {
        return record.sizeBytes() > maxFileSize;
    }
}