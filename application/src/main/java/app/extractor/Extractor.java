package app.extractor;

import app.model.FileRecord;

import java.io.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Extractor {

    private static final int DEFAULT_PREVIEW_LINES = 3;
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final int previewLines;
    private final long maxFileSize;

    public Extractor() {
        this(DEFAULT_PREVIEW_LINES, DEFAULT_MAX_FILE_SIZE);
    }

    public Extractor(int previewLines, long maxFileSize) {
        this.previewLines = previewLines;
        this.maxFileSize = maxFileSize;
    }

    public String extract(FileRecord record) {
        return String.join(System.lineSeparator(), readLines(record, Integer.MAX_VALUE));
    }

    public String preview(FileRecord record) {
        return String.join(System.lineSeparator(), readLines(record, previewLines));
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