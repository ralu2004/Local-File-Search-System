package app.service.search;

import app.db.Database;
import app.db.DatabaseProvider;
import app.model.RankedSearchResult;
import app.repository.FileSearchRepository;
import app.repository.SearchActivityRepository;
import app.search.SearchEngine;
import app.search.query.QueryParser;
import app.service.support.DatabaseAccessor;
import app.service.support.QueryNormalizer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service for search use cases.
 * Executes searches and publishes search events to registered observers.
 */
public class SearchService {

    private final DatabaseAccessor databaseAccessor;
    private final List<SearchObserver> searchObservers;

    public SearchService(DatabaseProvider databaseProvider) {
        this(databaseProvider, List.of(new SearchActivityObserver(databaseProvider)));
    }

    public SearchService(DatabaseProvider databaseProvider, List<SearchObserver> searchObservers) {
        this.databaseAccessor = new DatabaseAccessor(databaseProvider);
        this.searchObservers = new ArrayList<>(searchObservers);
    }

    /**
     * Executes a search and then records query activity for history-driven
     * suggestions and ranking.
     */
    public List<RankedSearchResult> search(String dbPath, String input, int limit) throws SQLException, IOException {
        try (Database db = databaseAccessor.openDatabase(dbPath)) {
            long startedAt = System.nanoTime();
            List<RankedSearchResult> results = executeSearch(db, input, limit);
            recordSearchActivity(dbPath, input, results.size(), startedAt);
            return results;
        }
    }

    /**
     * Returns prefix-based query suggestions from normalized search history.
     */
    public List<String> suggestQueries(String dbPath, String prefix, int limit) throws SQLException, IOException {
        try (Database db = databaseAccessor.openDatabase(dbPath)) {
            SearchActivityRepository activity = db;
            return activity.suggestQueries(QueryNormalizer.normalizeForHistory(prefix), limit);
        }
    }

    /**
     * Returns recent unique queries ordered by latest usage.
     */
    public List<String> recentQueries(String dbPath, int limit) throws SQLException, IOException {
        try (Database db = databaseAccessor.openDatabase(dbPath)) {
            SearchActivityRepository activity = db;
            return activity.recentQueries(limit);
        }
    }

    /**
     * Records that a user opened a file from a search results list.
     * <p>
     * Persists both raw and normalized query text together with file path,
     * optional result position, and open timestamp for history-based ranking.
     */
    public void recordResultOpen(String dbPath, String query, String filePath, Integer resultPosition) throws SQLException, IOException {
        String rawQuery = query == null ? "" : query;
        String normalizedQuery = QueryNormalizer.normalizeForHistory(rawQuery);
        String openedAt = LocalDateTime.now().toString();
        try (Database db = databaseAccessor.openDatabase(dbPath)) {
            SearchActivityRepository activity = db;
            activity.recordResultOpen(rawQuery, normalizedQuery, filePath, resultPosition, openedAt);
        }
    }

    private List<RankedSearchResult> executeSearch(FileSearchRepository searchRepo, String input, int limit) throws SQLException {
        SearchEngine engine = new SearchEngine(searchRepo, new QueryParser(), limit);
        return engine.search(input);
    }

    /**
     * Notifies observers with normalized query text, result count, and
     * end-to-end duration.
     */
    private void recordSearchActivity(String dbPath, String input, int resultCount, long startedAtNanos) throws SQLException, IOException {
        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        String rawQuery = input == null ? "" : input;
        String normalizedQuery = QueryNormalizer.normalizeForHistory(input);
        String executedAt = LocalDateTime.now().toString();
        for (SearchObserver observer : searchObservers) {
            observer.onSearchExecuted(dbPath, rawQuery, normalizedQuery, resultCount, durationMs, executedAt);
        }
    }

}
