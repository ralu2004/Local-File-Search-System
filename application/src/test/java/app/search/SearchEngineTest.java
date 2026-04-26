package app.search;

import app.model.SearchResult;
import app.repository.FileSearchRepository;
import app.search.query.Query;
import app.search.query.QueryParser;
import app.search.ranking.AlphabeticalRankingStrategy;
import app.search.ranking.DateRankingStrategy;
import app.search.ranking.RankingStrategy;
import app.search.ranking.StaticRankingStrategy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchEngineTest {

    @Test
    void sortDateUsesDateRankingStrategy() throws SQLException {
        CapturingRepository repository = new CapturingRepository();
        SearchEngine engine = new SearchEngine(repository, new QueryParser(), 25);

        engine.search("notes sort:date");

        assertNotNull(repository.capturedStrategy, "Expected ranking strategy to be passed to repository");
        assertInstanceOf(DateRankingStrategy.class, repository.capturedStrategy);
        assertEquals(25, repository.capturedLimit);
        assertEquals("notes sort:date", repository.capturedNormalizedQuery);
    }

    @Test
    void sortAlphaUsesAlphabeticalRankingStrategy() throws SQLException {
        CapturingRepository repository = new CapturingRepository();
        SearchEngine engine = new SearchEngine(repository, new QueryParser(), 10);

        engine.search("config sort:alpha");

        assertNotNull(repository.capturedStrategy, "Expected ranking strategy to be passed to repository");
        assertInstanceOf(AlphabeticalRankingStrategy.class, repository.capturedStrategy);
    }

    @Test
    void missingSortFallsBackToStaticRankingStrategy() throws SQLException {
        CapturingRepository repository = new CapturingRepository();
        SearchEngine engine = new SearchEngine(repository, new QueryParser(), 5);

        engine.search("config");

        assertNotNull(repository.capturedStrategy, "Expected ranking strategy to be passed to repository");
        assertInstanceOf(StaticRankingStrategy.class, repository.capturedStrategy);
    }

    @Test
    void invalidSortFallsBackToStaticRankingStrategy() throws SQLException {
        CapturingRepository repository = new CapturingRepository();
        SearchEngine engine = new SearchEngine(repository, new QueryParser(), 5);

        engine.search("config sort:unknown");

        assertNotNull(repository.capturedStrategy, "Expected ranking strategy to be passed to repository");
        assertInstanceOf(StaticRankingStrategy.class, repository.capturedStrategy);
    }

    @Test
    void queryNormalizationIsTrimmedAndLowercasedBeforeRepositoryCall() throws SQLException {
        CapturingRepository repository = new CapturingRepository();
        SearchEngine engine = new SearchEngine(repository, new QueryParser(), 10);

        engine.search("   DoCkEr Logs   ");

        assertEquals("docker logs", repository.capturedNormalizedQuery);
    }

    private static final class CapturingRepository implements FileSearchRepository {
        private int capturedLimit;
        private RankingStrategy capturedStrategy;
        private String capturedNormalizedQuery;

        @Override
        public List<SearchResult> search(Query query, int limit, RankingStrategy strategy, String normalizedQuery) {
            this.capturedLimit = limit;
            this.capturedStrategy = strategy;
            this.capturedNormalizedQuery = normalizedQuery;
            return List.of(new SearchResult(
                    Path.of("D:/dummy.txt"),
                    "dummy.txt",
                    "txt",
                    "preview",
                    LocalDateTime.parse("2026-01-01T00:00:00"),
                    12L
            ));
        }
    }
}
