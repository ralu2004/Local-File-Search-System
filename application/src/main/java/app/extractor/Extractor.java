package app.extractor;

import app.model.FileRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Extractor {

    private static final int PREVIEW_LINES = 3;

    public String extract(FileRecord record) {
        return String.join(System.lineSeparator(), readLines(record, Integer.MAX_VALUE));
    }

    public String preview(FileRecord record) {
        return String.join(System.lineSeparator(), readLines(record, PREVIEW_LINES));
    }

    private List<String> readLines(FileRecord record, int maxLines) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(record.path().toFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null && lines.size() < maxLines) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Could not read file: " + record.path());
        }
        return lines;
    }
}