package app.crawler;

import app.model.FileRecord;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;

import app.util.FileTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recursively traverses a root directory and emits indexable file metadata.
 * <p>
 * Applies built-in and user-provided ignore rules and skips non-text files (as requested in the 1st iteration).
 */
public class Crawler {

    private static final Logger log = LoggerFactory.getLogger(Crawler.class);
    private final Path root;
    private final List<PathMatcher> matchers;
    private static final List<String> DEFAULT_IGNORE_RULES = List.of(
            "node_modules",
            ".npm",
            ".yarn",
            ".pnpm-store",
            "target",
            "build",
            "dist",
            "out",
            ".gradle",
            ".mvn",
            ".git",
            ".svn",
            ".hg",
            ".idea",
            ".vscode",
            ".vs",
            "Windows",
            "Program Files",
            "Program Files (x86)",
            "ProgramData",
            "AppData",
            "System Volume Information",
            "$Recycle.Bin",
            "Recovery",
            "Temp",
            "tmp",
            ".cache",
            "__pycache__",
            ".pytest_cache",
            "*.min.js",
            "*.bundle.js",
            "*.min.css"
    );

    public Path getRoot() {
        return root;
    }

    public Crawler(Path root, List<String> ignoreRules) {
        this.root = root;
        List<String> allRules = new ArrayList<>(DEFAULT_IGNORE_RULES);
        allRules.addAll(ignoreRules);
        this.matchers = allRules.stream()
                .map(rule -> FileSystems.getDefault().getPathMatcher("glob:" + rule))
                .toList();
    }

    public void crawl(Consumer<FileRecord> sink) {
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Root directory does not exist: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Root path is not a directory: " + root);
        }

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            if (isIgnoredDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!isIgnoredFile(file)) {
                                sink.accept(buildRecord(file, attrs));
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) {
                            if (exc instanceof FileSystemLoopException) {
                                log.warn("Symlink loop detected, skipping: {}", file);
                            } else {
                                log.warn("Could not access file, skipping: {} ({})", file, exc.getMessage());
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to crawl directory: " + root, e);
        }
    }

    private FileRecord buildRecord(Path file, BasicFileAttributes attrs) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        String extension = dotIndex == -1 ? "" : name.substring(dotIndex + 1);
        LocalDateTime createdAt = attrs.creationTime()
                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime modifiedAt = attrs.lastModifiedTime()
                .toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return new FileRecord(file, name, extension, attrs.size(), createdAt, modifiedAt);
    }

    private boolean matchesIgnoreRule(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) return false;

        Path relative = root.relativize(path);
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(relative) || matcher.matches(relative.getFileName())) return true;
        }
        return false;
    }

    private boolean isIgnoredDirectory(Path dir) {
        Path fileName = dir.getFileName();
        if (fileName == null) return false;

        String name = fileName.toString();
        if (name.startsWith(".")) return true;
        return matchesIgnoreRule(dir);
    }

    private boolean isIgnoredFile(Path file) {
        Path fileName = file.getFileName();
        if (fileName == null) return false;

        String name = fileName.toString();
        if (name.startsWith(".")) return true;
        if (matchesIgnoreRule(file)) return true;

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) return true;
        String extension = name.substring(dotIndex + 1).toLowerCase();
        return !FileTypes.TEXT_EXTENSIONS.contains(extension);
    }
}