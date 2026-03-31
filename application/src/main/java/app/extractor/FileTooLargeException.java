package app.extractor;

import java.nio.file.Path;

/**
 * Signals that a file exceeds the configured maximum size for extraction.
 */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(Path path, long size, long maxSize) {
        super("File too large to index: " + path +
                " (" + size / 1024 / 1024 + "MB, max: " + maxSize / 1024 / 1024 + "MB)");
    }
}
