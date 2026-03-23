package app.crawler;

import app.model.FileRecord;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import app.util.FileTypes;

public class Crawler {

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

    public Crawler(Path root, List<String> ignoreRules) {
        this.root = root;
        List<String> allRules = new ArrayList<>(DEFAULT_IGNORE_RULES);
        allRules.addAll(ignoreRules);
        this.matchers = allRules.stream()
                .map(rule -> FileSystems.getDefault().getPathMatcher("glob:" + rule))
                .toList();
    }

    public Stream<FileRecord> crawl() {
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Root directory does not exist: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Root path is not a directory: " + root);
        }

        final Object end = new Object();
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();

        Thread producer = Thread.ofVirtual().start(() -> {
            try {
                Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                        new SimpleFileVisitor<>() {

                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                                if (isIgnored(dir)) return FileVisitResult.SKIP_SUBTREE;
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (!isIgnored(file)) {
                                    try {
                                        queue.put(buildRecord(file, attrs));
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        return FileVisitResult.TERMINATE;
                                    }
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                                if (exc instanceof FileSystemLoopException) {
                                    System.err.println("Symlink loop detected, skipping: " + file);
                                } else {
                                    System.err.println("Could not access file, skipping: " + file);
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                try {
                    queue.put(new IllegalStateException("Failed to crawl directory: " + root, e));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                try {
                    queue.put(end);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        Iterator<FileRecord> iterator = new Iterator<>() {
            private FileRecord next;
            private boolean finished;

            @Override
            public boolean hasNext() {
                if (finished) return false;
                if (next != null) return true;

                try {
                    Object item = queue.take();
                    if (item == end) {
                        finished = true;
                        return false;
                    }
                    if (item instanceof RuntimeException ex) {
                        finished = true;
                        throw ex;
                    }
                    next = (FileRecord) item;
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finished = true;
                    return false;
                }
            }

            @Override
            public FileRecord next() {
                if (!hasNext()) throw new NoSuchElementException();
                FileRecord current = next;
                next = null;
                return current;
            }
        };

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false
        ).onClose(producer::interrupt);
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

    private boolean isIgnored(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) return false;

        String name = fileName.toString();
        if (name.startsWith(".")) return true;

        Path relative = root.relativize(path);
        for (PathMatcher matcher : matchers) {
            if (matcher.matches(relative) || matcher.matches(relative.getFileName())) return true;
        }

        if (Files.isDirectory(path)) return false;

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) return true;
        String extension = name.substring(dotIndex + 1).toLowerCase();
        return !FileTypes.TEXT_EXTENSIONS.contains(extension);
    }
}