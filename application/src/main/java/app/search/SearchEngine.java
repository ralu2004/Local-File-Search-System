package app.search;

import app.model.SearchResult;
import app.repository.FileRepository;
import app.search.query.Query;
import app.search.query.QueryParser;

import java.sql.SQLException;
import java.util.List;

public class SearchEngine {

    private final FileRepository repository;
    private final QueryParser parser;

    public SearchEngine(FileRepository repository) {
        this(repository, new QueryParser());
    }

    public SearchEngine(FileRepository repository, QueryParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    public List<SearchResult> search(String input) {
        try {
            Query query = parser.parse(input);
            return repository.search(query);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid query: " + e.getMessage());
            return List.of();
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
            return List.of();
        }
    }
}