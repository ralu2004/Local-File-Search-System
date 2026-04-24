package app.service;

import app.db.Database;
import app.db.DatabaseProvider;
import app.model.SearchResult;
import app.search.SearchEngine;
import app.search.query.QueryParser;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Application service for search use cases.
 */
public class SearchService {
    
    private final DatabaseProvider databaseProvider;

    public SearchService(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    /**
     * Executes a search and then records query activity for history-driven
     * suggestions and ranking.
     */
    public List<SearchResult> search(String dbPath, String input, int limit) throws SQLException, IOException {
        try (Database db = openDatabase(dbPath)) {
            long startedAt = System.nanoTime();
            List<SearchResult> results = executeSearch(db, input, limit);
            recordSearchActivity(db, input, results.size(), startedAt);
            return results;
        }
    }

    private List<SearchResult> executeSearch(Database db, String input, int limit) throws SQLException {
        SearchEngine engine = new SearchEngine(db, new QueryParser(), limit);
        return engine.search(input);
    }

    /**
     * Persists query activity with normalized query text, result count, and
     * end-to-end duration.
     */
    private void recordSearchActivity(Database db, String input, int resultCount, long startedAtNanos) throws SQLException {
        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        String rawQuery = input == null ? "" : input;
        String normalizedQuery = normalizeQuery(input);
        db.recordSearch(rawQuery, normalizedQuery, resultCount, durationMs, LocalDateTime.now().toString());
    }

    /**
     * Returns prefix-based query suggestions from normalized search history.
     */
    public List<String> suggestQueries(String dbPath, String prefix, int limit) throws SQLException, IOException {
        try (Database db = openDatabase(dbPath)) {
            return db.suggestQueries(normalizeQuery(prefix), limit);
        }
    }

    /**
     * Returns recent unique queries ordered by latest usage.
     */
    public List<String> recentQueries(String dbPath, int limit) throws SQLException, IOException {
        try (Database db = openDatabase(dbPath)) {
            return db.recentQueries(limit);
        }
    }

    private Database openDatabase(String dbPath) throws SQLException, IOException {
        if (dbPath == null || dbPath.isBlank()) {
            return databaseProvider.openDefault();
        }
        return databaseProvider.open(dbPath);
    }

    /**
     * Normalizes query text for case-insensitive prefix matching in history.
     */
    private String normalizeQuery(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase();
    }
}
