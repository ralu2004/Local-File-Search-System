package app.search;

import app.db.Database;
import app.model.SearchResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SearchEngine {

    private final Database db;

    public SearchEngine(Database db) {
        this.db = db;
    }

    public List<SearchResult> search(String query) {
        try {
            return db.search(query);
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
            return List.of();
        }
    }
}
