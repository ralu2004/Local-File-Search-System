package app.model;

/**
 * File metadata paired with extracted full content and preview.
 */
public record ExtractedRecord(FileRecord record, String content, String preview) {}