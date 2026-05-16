package app.extractor.strategy;

import app.model.ExtractedRecord;
import app.model.FileRecord;

import java.io.IOException;

/**
 * Strategy for processing a file during indexing.
 * <p>
 * Each implementation handles a specific file type (e.g. text, image)
 * and is responsible for extracting all indexable data from that file.
 * The {@link app.extractor.Extractor} dispatches to the first strategy
 * that returns {@code true} from {@link #supports(FileRecord)}.
 */
public interface FileProcessingStrategy {

    /** Returns {@code true} if this strategy can process the given file. */
    boolean supports(FileRecord record);
    /** Processes the file and returns an {@link ExtractedRecord} ready for indexing.*/
    ExtractedRecord process(FileRecord record) throws IOException;
}