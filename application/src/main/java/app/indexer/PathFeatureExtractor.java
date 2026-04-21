package app.indexer;

import app.search.ranking.PathFeatures;

import java.nio.file.Path;
import java.util.Set;

/**
 * Extracts normalized path feature signals from a file path at index time.
 * <p>
 * Each signal is a value in [0.0, 1.0] and is stored in the {@code path_features} table.
 * At query time, a {@link app.search.ranking.RankingStrategy} combines these signals
 * into a final ordering score.
 */
public class PathFeatureExtractor {

    private static final Set<String> BOOSTED_EXTENSIONS = Set.of(
            "java", "kt", "py", "ts", "js", "cs", "cpp", "c", "go", "rs",
            "md", "txt", "json", "xml", "yaml", "yml", "toml", "sql", "html", "css"
    );

    private static final Set<String> PENALIZED_EXTENSIONS = Set.of(
            "log", "tmp", "bak", "cache", "lock", "class", "pyc", "o", "out"
    );

    private static final Set<String> BOOSTED_DIRECTORIES = Set.of(
            "src", "main", "docs", "doc", "lib", "api", "core", "app", "resources"
    );

    private static final Set<String> PENALIZED_DIRECTORIES = Set.of(
            "target", "build", "dist", "out", "bin", "node_modules",
            "cache", ".git", ".idea", "__pycache__", ".gradle"
    );

    public PathFeatures extract(Path path) {
        return new PathFeatures(
                scoreDepth(path),
                scoreExtension(path),
                scoreDirectories(path),
                scoreFilename(path)
        );
    }

    private double scoreDepth(Path path) {
        int depth = path.getNameCount();
        if (depth <= 3)  return 1.0;
        if (depth <= 5)  return 0.8;
        if (depth <= 7)  return 0.5;
        if (depth <= 10) return 0.3;
        return 0.1;
    }

    private double scoreExtension(Path path) {
        String filename = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return 0.5;

        String ext = filename.substring(dot + 1).toLowerCase();
        if (BOOSTED_EXTENSIONS.contains(ext))   return 1.0;
        if (PENALIZED_EXTENSIONS.contains(ext)) return 0.1;
        return 0.5;
    }

    private double scoreDirectories(Path path) {
        int nameCount = path.getNameCount();
        if (nameCount <= 1) return 0.5;

        int boosts    = 0;
        int penalties = 0;

        for (int i = 0; i < nameCount - 1; i++) {
            String segment = path.getName(i).toString().toLowerCase();
            if (BOOSTED_DIRECTORIES.contains(segment))   boosts++;
            if (PENALIZED_DIRECTORIES.contains(segment)) penalties++;
        }

        if (penalties > 0) return 0.1;
        if (boosts > 0)    return 1.0;
        return 0.5;
    }
    
    private double scoreFilename(Path path) {
        String filename = path.getFileName() == null ? "" : path.getFileName().toString();
        if (filename.isEmpty()) return 0.0;

        int dot = filename.lastIndexOf('.');
        String name = dot > 0 ? filename.substring(0, dot) : filename;

        int len = name.length();
        if (len <= 20) return 1.0;
        if (len <= 40) return 0.7;
        if (len <= 60) return 0.4;
        return 0.2;
    }
}