package app.indexer;

import app.model.FileRecord;

record ExtractedRecord(FileRecord record, String content, String preview) {}