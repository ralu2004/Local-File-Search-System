package app.cli;

import app.crawler.Crawler;
import app.db.Database;
import app.extractor.Extractor;
import app.indexer.IndexReport;
import app.indexer.Indexer;
import app.model.SearchResult;
import app.search.SearchEngine;
import app.search.query.QueryParser;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.nio.file.Path;
import java.util.List;

@Command(
        name = "search-engine",
        description = "A local file search engine",
        subcommands = {CLI.IndexCommand.class, CLI.SearchCommand.class},
        mixinStandardHelpOptions = true
)
public class CLI implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "index", description = {
            "Index files in a directory.",
            "",
            "Options:",
            "  --db             Custom database path",
            "  -i, --ignore     Glob patterns to ignore (e.g. *.log, target)",
            "  --max-file-size  Maximum file size in MB to index (default: 10)",
            "  --preview-lines  Number of preview lines to store (default: 3)",
            "  --threads        Number of indexing threads (default: CPU count)",
            "",
            "Examples:",
            "  index C:\\Users\\user\\Documents",
            "  index C:\\projects -i target -i *.log --max-file-size 5"
    })
    static class IndexCommand implements Runnable {

        @Option(names = {"--db"}, description = "Custom database path (default: .searchengine/index.db)")
        private String dbPath;

        @Parameters(index = "0", description = "Root directory to index")
        private Path root;

        @Option(names = {"-i", "--ignore"}, description = "Glob patterns to ignore")
        private List<String> ignoreRules = List.of();

        @Option(names = {"--max-file-size"}, description = "Max file size in MB (default: 10)")
        private int maxFileSizeMb = 10;

        @Option(names = {"--preview-lines"}, description = "Number of preview lines (default: 3)")
        private int previewLines = 3;

        @Option(names = {"--threads"}, description = "Number of indexing threads (default: CPU count)")
        private int threads = Runtime.getRuntime().availableProcessors();

        @Override
        public void run() {
            try (Database db = dbPath != null ? new Database(dbPath) : new Database()) {
                Crawler crawler = new Crawler(root, ignoreRules);
                Extractor extractor = new Extractor(previewLines, (long) maxFileSizeMb * 1024 * 1024);
                Indexer indexer = new Indexer(db, db, crawler, extractor, threads);

                System.out.println("Indexing " + root + "...");
                IndexReport report = indexer.run();

                System.out.println("\nIndexing complete!");
                System.out.println("─────────────────────────────────");
                System.out.println(" Total:   " + report.totalFiles());
                System.out.println(" Indexed: " + report.indexed());
                System.out.println(" Skipped: " + report.skipped());
                System.out.println(" Failed:  " + report.failed());
                System.out.println(" Deleted: " + report.deleted());
                System.out.println(" Time:    " + report.elapsed().toSeconds() + "s");
                System.out.println("─────────────────────────────────");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "search", description = {
            "Search indexed files.",
            "",
            "Query syntax:",
            "  <term>              Full-text search",
            "  <filename.ext>      Search by filename",
            "  ext:<extension>     Filter by extension",
            "  modified:<date>     Filter by date (YYYY-MM-DD)",
            "  size:<bytes>        Filter by file size",
            "",
            "Examples:",
            "  search \"getting started\"",
            "  search README.md",
            "  search ext:java",
            "  search \"config ext:json\""
    })
    static class SearchCommand implements Runnable {

        @Option(names = {"--db"}, description = "Custom database path (default: .searchengine/index.db)")
        private String dbPath;

        @Parameters(index = "0", description = "Search query")
        private String query;

        @Option(names = {"--limit"}, description = "Maximum number of results (default: 50)")
        private int limit = 50;

        @Override
        public void run() {
            try (Database db = dbPath != null ? new Database(dbPath) : new Database()) {
                SearchEngine engine = new SearchEngine(db, new QueryParser(), limit);
                List<SearchResult> results = engine.search(query);

                if (results.isEmpty()) {
                    System.out.println("No results found for: " + query);
                    return;
                }

                System.out.println("\nFound " + results.size() + " result(s) for: \"" + query + "\"");
                for (SearchResult result : results) {
                    System.out.println("─────────────────────────────────");
                    System.out.println(" " + result.filename() + "  [" + result.extension() + "]  Modified: " + result.modifiedAt().toLocalDate());
                    System.out.println(" Path: " + result.path());
                    System.out.println(" Preview:");
                    for (String line : result.preview().split(System.lineSeparator())) {
                        System.out.println("   " + line);
                    }
                }
                System.out.println("─────────────────────────────────");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLI()).execute(args);
        System.exit(exitCode);
    }
}