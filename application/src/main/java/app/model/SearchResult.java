package app.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record SearchResult(
        Path path,
        String filename,
        String extension,
        String preview,
        LocalDateTime modifiedAt,
        Long sizeBytes
) {}
