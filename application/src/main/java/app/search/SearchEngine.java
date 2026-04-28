package app.search;

import app.model.RankedSearchResult;
import app.repository.FileSearchRepository;
import app.search.query.Query;
import app.search.query.QueryParser;
import app.search.ranking.RankingStrategy;
import app.search.ranking.RankingStrategyResolver;
import app.service.support.QueryNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Executes parsed search queries against a {@link app.repository.FileSearchRepository}.
 * <p>
 * Uses {@link app.search.query.QueryParser} to interpret the user input syntax and
 * returns ranked hits as {@link app.model.RankedSearchResult} values.
 */
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

    /**
     * Parses user input, resolves the requested ranking strategy, and executes
     * repository search with a normalized history key.
     * <p>
     * Invalid query syntax is treated as a user input issue and returns an
     * empty result set.
     */
    public List<RankedSearchResult> search(String input) throws SQLException {
        try {
            Query query = parser.parse(input);
            RankingStrategy rankingStrategy = RankingStrategyResolver.getRankingStrategy(query.filters().get("sort"));
            String normalizedQuery = QueryNormalizer.normalizeForHistory(input);
            return repository.search(query, limit, rankingStrategy, normalizedQuery);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid query input: {}", e.getMessage());
            return List.of();
        }
    }
}