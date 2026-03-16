package app.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record FileRecord(
        Path path,
        String filename,
        String extension,
        long sizeBytes,
        LocalDateTime createdAt,
        LocalDateTime modifiedAt
) {}