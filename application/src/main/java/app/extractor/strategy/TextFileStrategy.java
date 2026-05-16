package app.extractor.strategy;

import app.indexer.PathFeatureExtractor;
import app.extractor.FileTooLargeException;
import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.search.ranking.PathFeatures;
import app.util.FileTypes;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FileProcessingStrategy} for plain text files.
 * <p>
 * Reads file content line by line, extracts a preview snippet,
 * and computes path-based ranking features via {@link PathFeatureExtractor}.
 */
public class TextFileStrategy implements FileProcessingStrategy {

    private final int previewLines;
    private final long maxFileSize;
    private final PathFeatureExtractor pathFeatureExtractor;

    /**
     * @param previewLines     number of lines to include in the preview snippet
     * @param maxFileSize      maximum file size in bytes; larger files are skipped
     * @param pathFeatureExtractor extracts ranking signals from the file path
     */
    public TextFileStrategy(int previewLines, long maxFileSize, PathFeatureExtractor pathFeatureExtractor) {
        this.previewLines = previewLines;
        this.maxFileSize = maxFileSize;
        this.pathFeatureExtractor = pathFeatureExtractor;
    }

    @Override
    public boolean supports(FileRecord record) {
        return FileTypes.TEXT_EXTENSIONS.contains(record.extension().toLowerCase());
    }

    @Override
    public ExtractedRecord process(FileRecord record) throws IOException {
        if (record.sizeBytes() > maxFileSize) {
            throw new FileTooLargeException(record.path(), record.sizeBytes(), maxFileSize);
        }
        List<String> lines = readLines(record);
        String content = String.join(System.lineSeparator(), lines);
        int previewLimit = Math.min(previewLines, lines.size());
        String preview = String.join(System.lineSeparator(), lines.subList(0, previewLimit));
        PathFeatures features = pathFeatureExtractor.extract(record.path());
        return new ExtractedRecord(record, content, preview, features, null);
    }

    private List<String> readLines(FileRecord record) throws IOException {
        List<String> lines = new ArrayList<>();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(record.path().toFile()), decoder))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}