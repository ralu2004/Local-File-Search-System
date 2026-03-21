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

    @Command(name = "index", description = "Index files in a directory")
    static class IndexCommand implements Runnable {

        @Parameters(index = "0", description = "Root directory to index")
        private Path root;

        @Option(names = {"-i", "--ignore"}, description = "Glob patterns to ignore")
        private List<String> ignoreRules = List.of();

        @Override
        public void run() {
            try (Database db = new Database()) {
                Crawler crawler = new Crawler(root, ignoreRules);
                Extractor extractor = new Extractor();
                Indexer indexer = new Indexer(db, crawler, extractor);

                System.out.println("Indexing " + root + "...");
                IndexReport report = indexer.run();

                System.out.println("Indexing finished with");
                System.out.println("-- total files: " + report.totalFiles());
                System.out.println("-- indexed: " + report.indexed());
                System.out.println("-- skipped: " + report.skipped());
                System.out.println("-- failed: " + report.failed());
                System.out.println("-- deleted: " + report.deleted());
                System.out.println(report.elapsed().toSeconds() + " s");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "search", description = "Search indexed files")
    static class SearchCommand implements Runnable {

        @Parameters(index = "0", description = "Search query")
        private String query;

        @Override
        public void run() {
            try (Database db = new Database()) {
                SearchEngine engine = new SearchEngine(db);
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