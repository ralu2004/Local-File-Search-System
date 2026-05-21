package app.extractor;

import app.extractor.strategy.FileProcessingStrategy;
import app.extractor.strategy.ImageFileStrategy;
import app.extractor.strategy.TextFileStrategy;
import app.indexer.PathFeatureExtractor;
import app.model.ExtractedRecord;
import app.model.FileRecord;

import java.io.IOException;
import java.util.List;

/**
 * Dispatches file processing to the appropriate {@link FileProcessingStrategy}
 * based on file type.
 * <p>
 * Strategies are evaluated in order; the first one whose
 * {@link FileProcessingStrategy#supports(FileRecord)} returns {@code true}
 * is used to process the file.
 * <p>
 * To support additional file types, construct an {@code Extractor} with a
 * custom strategy list via {@link #Extractor(List)}.
 */
public class Extractor {

    private static final int DEFAULT_PREVIEW_LINES = 3;
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final List<FileProcessingStrategy> strategies;

    public Extractor() {
        this(DEFAULT_PREVIEW_LINES, DEFAULT_MAX_FILE_SIZE);
    }

    public Extractor(int previewLines, long maxFileSize) {
        this(previewLines, maxFileSize, new PathFeatureExtractor());
    }

    public Extractor(int previewLines, long maxFileSize, PathFeatureExtractor pathFeatureExtractor) {
        this.strategies = List.of(
                new TextFileStrategy(previewLines, maxFileSize, pathFeatureExtractor),
                new ImageFileStrategy(pathFeatureExtractor)
        );
    }

    /**
     * Constructs an {@code Extractor} with a fully custom strategy list.
     * Strategies are evaluated in registration order.
     *
     * @param strategies the ordered list of strategies to use
     */
    public Extractor(List<FileProcessingStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public ExtractedRecord extractWithPreview(FileRecord record) throws IOException {
        for (FileProcessingStrategy strategy : strategies) {
            if (strategy.supports(record)) {
                return strategy.process(record);
            }
        }
        throw new IllegalArgumentException("No strategy found for file: " + record.path());
    }
}