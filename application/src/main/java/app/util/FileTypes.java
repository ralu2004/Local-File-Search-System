package app.util;

import java.util.Set;

/**
 * Shared allow-list of text-like file extensions considered indexable.
 */
public final class FileTypes {

    public static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "java", "xml", "json", "csv", "html", "htm",
            "css", "js", "ts", "py", "c", "cpp", "h", "hpp", "rb",
            "yaml", "yml", "toml", "ini", "cfg", "properties", "sh",
            "bat", "sql", "gradle", "kt", "rs", "go", "swift"
    );

    private FileTypes() {}
}