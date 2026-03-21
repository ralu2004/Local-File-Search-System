package app.extractor;

import java.nio.file.Path;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(Path path, long size, long maxSize) {
        super("File too large to index: " + path +
                " (" + size / 1024 / 1024 + "MB, max: " + maxSize / 1024 / 1024 + "MB)");
    }
}
