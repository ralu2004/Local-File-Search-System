package app.search;

import app.model.RankedSearchResult;
import app.repository.FileSearchRepository;
import app.search.query.Query;
import app.search.query.QueryParser;
import app.search.query.preprocessor.IdentityDecorator;
import app.search.query.preprocessor.LogicDecorator;
import app.search.query.preprocessor.SanitizationDecorator;
import app.search.ranking.RankingStrategy;
import app.search.ranking.RankingStrategyResolver;
import app.search.query.preprocessor.QueryDecorator;
import app.search.query.preprocessor.SynonymDecorator;
import app.service.support.QueryNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Executes search queries against a {@link app.repository.FileSearchRepository}.
 * <p>
 * Parses raw user input via {@link app.search.query.QueryParser}, then applies
 * a {@link app.search.query.preprocessor.QueryDecorator} pipeline that sanitizes,
 * expands synonyms, and normalizes the query into a valid FTS5 expression before
 * execution. Returns ranked hits as {@link app.model.RankedSearchResult} values.
 */
public class SearchEngine {

    private static final Logger log = LoggerFactory.getLogger(SearchEngine.class);
    private static final int DEFAULT_LIMIT = 50;
    private final int limit;

    private final FileSearchRepository repository;
    private final QueryParser parser;
    private final QueryDecorator preProcessor;

    public SearchEngine(FileSearchRepository repository) {
        this(repository, new QueryParser(), DEFAULT_LIMIT);
    }

    public SearchEngine(FileSearchRepository repository, QueryParser parser, int limit) {
        this.repository = repository;
        this.parser = parser;
        this.limit = limit;

        this.preProcessor = new SanitizationDecorator(
                new SynonymDecorator(
                        new LogicDecorator(
                                new IdentityDecorator()
                        ),
                        Map.of(
                                "img", List.of("img", "image", "photo", "picture"),
                                "doc", List.of("doc", "document", "docx", "pdf"),
                                "vid", List.of("vid", "video", "mp4", "mov")
                        )
                )
        );
    }

    /**
     * Parses user input, applies the query decorator pipeline, resolves the requested ranking
     * strategy, and executes repository search with a normalized history key.
     * <p>
     * The decorator pipeline runs after parsing and transforms the query value through sanitization,
     * synonym expansion, and FTS5 normalization before the query reaches the repository.
     * <p>
     * Invalid query syntax is treated as a user input issue and returns an empty result set.
     */
    public List<RankedSearchResult> search(String input) throws SQLException {
        try {
            Query query = parser.parse(input);
            query = preProcessor.decorate(query);
            RankingStrategy rankingStrategy = RankingStrategyResolver.getRankingStrategy(query.filters().get("sort"));
            String normalizedQuery = QueryNormalizer.normalizeForHistory(input);
            return repository.search(query, limit, rankingStrategy, normalizedQuery);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid query input: {}", e.getMessage());
            return List.of();
        }
    }
}
