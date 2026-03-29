package app.service;

import app.db.Database;
import app.db.DatabaseProvider;
import app.model.SearchResult;
import app.search.SearchEngine;
import app.search.query.QueryParser;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Application service for search use cases.
 */
public class SearchService {
    private final DatabaseProvider databaseProvider;

    public SearchService(DatabaseProvider databaseProvider) {
        this.databaseProvider = databaseProvider;
    }

    public List<SearchResult> search(String dbPath, String input, int limit) throws SQLException, IOException {
        try (Database db = openDatabase(dbPath)) {
            SearchEngine engine = new SearchEngine(db, new QueryParser(), limit);
            return engine.search(input);
        }
    }

    private Database openDatabase(String dbPath) throws SQLException, IOException {
        if (dbPath == null || dbPath.isBlank()) {
            return databaseProvider.openDefault();
        }
        return databaseProvider.open(dbPath);
    }
}
