package app.db;

import app.search.query.Query;
import app.search.query.QueryType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QueryBuilder} covering path and content filter SQL generation.
 */
class QueryBuilderTest {

    private final QueryBuilder builder = new QueryBuilder();

    @Test
    void contentFilterAloneProducesFtsMatchWithColumnPrefix() {
        Query query = new Query(QueryType.METADATA, null, Map.of("content", "hello"));
        BuiltQuery built = builder.build(query, 10);

        assertTrue(built.sql().contains("files_fts MATCH ?"),
                "Expected FTS MATCH in SQL");
        assertTrue(built.params().contains("content:hello"),
                "Expected FTS param to be 'content:hello'");
        assertFalse(built.sql().contains("WHERE f."),
                "Should not have a plain WHERE clause for content-only filter");
    }

    @Test
    void contentFilterWithFreeTermCombinesBothInFtsMatch() {
        Query query = new Query(QueryType.MIXED, "hello", Map.of("content", "world"));
        BuiltQuery built = builder.build(query, 10);

        assertTrue(built.sql().contains("files_fts MATCH ?"),
                "Expected FTS MATCH in SQL");

        String ftsParam = built.params().stream()
                .filter(p -> p instanceof String s && s.contains("content:"))
                .map(Object::toString)
                .findFirst()
                .orElse(null);

        assertNotNull(ftsParam, "Expected a combined FTS param");
        assertTrue(ftsParam.contains("hello"), "FTS param should contain the free term");
        assertTrue(ftsParam.contains("AND content:world"), "FTS param should contain content column filter");
    }

    @Test
    void contentFilterDoesNotAppearAsWhereCondition() {
        Query query = new Query(QueryType.METADATA, null, Map.of("content", "secret"));
        BuiltQuery built = builder.build(query, 10);

        assertFalse(built.sql().contains("f.content"),
                "content: should never appear as a WHERE condition on the files table");
    }

    @Test
    void pathFilterAloneProducesLikeConditionWithWildcards() {
        Query query = new Query(QueryType.METADATA, null, Map.of("path", "Documents/Work"));
        BuiltQuery built = builder.build(query, 10);

        assertTrue(built.sql().contains("f.path LIKE ?"),
                "Expected LIKE condition on f.path");
        assertTrue(built.params().contains("%Documents/Work%"),
                "Expected wildcard-wrapped path param");
        assertFalse(built.sql().contains("files_fts MATCH"),
                "Path-only filter should not trigger FTS");
    }

    @Test
    void pathFilterWithFreeTermProducesBothFtsAndLike() {
        Query query = new Query(QueryType.MIXED, "report", Map.of("path", "Finance"));
        BuiltQuery built = builder.build(query, 10);

        assertTrue(built.sql().contains("files_fts MATCH ?"),
                "Expected FTS MATCH for free term");
        assertTrue(built.sql().contains("f.path LIKE ?"),
                "Expected LIKE condition for path filter");
        assertTrue(built.params().contains("%Finance%"),
                "Expected wildcard-wrapped path param");
    }

    @Test
    void pathAndContentFiltersTogetherProduceFtsMatchAndLike() {
        Query query = new Query(QueryType.METADATA, null, Map.of("path", "Projects", "content", "TODO"));
        BuiltQuery built = builder.build(query, 10);

        assertTrue(built.sql().contains("files_fts MATCH ?"),
                "Expected FTS MATCH for content filter");
        assertTrue(built.sql().contains("f.path LIKE ?"),
                "Expected LIKE condition for path filter");
        assertTrue(built.params().contains("content:TODO"),
                "Expected content FTS param");
        assertTrue(built.params().contains("%Projects%"),
                "Expected wildcard-wrapped path param");
    }

    @Test
    void contentFilterAloneOrdersByRank() {
        Query query = new Query(QueryType.METADATA, null, Map.of("content", "hello"));
        BuiltQuery built = builder.build(query, 10);

        assertTrue(built.sql().contains("ORDER BY m.rank"),
                "Content-only filter should still order by FTS rank");
    }

    @Test
    void pathFilterAloneDoesNotOrderByRank() {
        Query query = new Query(QueryType.METADATA, null, Map.of("path", "Documents"));
        BuiltQuery built = builder.build(query, 10);

        assertFalse(built.sql().contains("ORDER BY m.rank"),
                "Path-only filter should not order by FTS rank");
    }
}