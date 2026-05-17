package app.util;

import java.util.Set;

/**
 * Shared allow-lists of indexable file extensions.
 * Covers both text files and image files supported by the indexing pipeline.
 */
public final class FileTypes {

    public static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "java", "xml", "json", "csv", "html", "htm",
            "css", "js", "ts", "py", "c", "cpp", "h", "hpp", "rb",
            "yaml", "yml", "toml", "ini", "cfg", "properties", "sh",
            "bat", "sql", "gradle", "kt", "rs", "go", "swift"
    );

    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    public static boolean isIndexable(String extension) {
        return TEXT_EXTENSIONS.contains(extension) || IMAGE_EXTENSIONS.contains(extension);
    }
}
