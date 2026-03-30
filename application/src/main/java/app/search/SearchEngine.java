package app.search;

import app.model.SearchResult;
import app.repository.FileSearchRepository;
import app.search.query.Query;
import app.search.query.QueryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class SearchEngine {

    private static final Logger log = LoggerFactory.getLogger(SearchEngine.class);
    private final FileSearchRepository repository;
    private final QueryParser parser;
    private static final int DEFAULT_LIMIT = 50;
    private final int limit;

    public SearchEngine(FileSearchRepository repository) {
        this(repository, new QueryParser(), DEFAULT_LIMIT);
    }

    public SearchEngine(FileSearchRepository repository, QueryParser parser, int limit) {
        this.repository = repository;
        this.parser = parser;
        this.limit = limit;
    }

    public List<SearchResult> search(String input) throws SQLException {
        try {
            Query query = parser.parse(input);
            return repository.search(query, limit);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid query input: {}", e.getMessage());
            return List.of();
        }
    }
}