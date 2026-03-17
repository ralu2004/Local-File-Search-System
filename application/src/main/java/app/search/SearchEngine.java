package app.search;

import app.db.Database;
import app.model.SearchResult;
import app.search.query.Query;
import app.search.query.QueryParser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SearchEngine {

    private final Database db;
    private final QueryParser parser;

    public SearchEngine(Database db, QueryParser parser) {
        this.db = db;
        this.parser = parser;
    }

    public List<SearchResult> search(String input) {
        try {
            Query query = parser.parse(input);
            return db.search(query);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid query: " + e.getMessage());
            return List.of();
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
            return List.of();
        }
    }
}
