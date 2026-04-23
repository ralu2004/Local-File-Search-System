package app.search.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies general query parsing behavior for full-text, filename, metadata,
 * mixed queries, and invalid input.
 */
class QueryParserGeneralTest {

    @Test
    void parse_blankOrNullInput_throws() {
        QueryParser parser = new QueryParser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    void parse_fullTextInput_returnsFullTextQuery() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("hello world");
        assertEquals(QueryType.FULLTEXT, q.type());
        assertEquals("hello world", q.value());
        assertTrue(q.filters().isEmpty());
    }

    @Test
    void parse_filenameInput_returnsFilenameQuery() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("README.md");
        assertEquals(QueryType.FILENAME, q.type());
        assertEquals("README.md", q.value());
        assertTrue(q.filters().isEmpty());
    }

    @Test
    void parse_metadataOnlyInput_returnsMetadataQuery() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("ext:java modified:2024-01-01");
        assertEquals(QueryType.METADATA, q.type());
        assertNull(q.value());
        assertEquals("java", q.filters().get("ext"));
        assertEquals("2024-01-01", q.filters().get("modified"));
    }

    @Test
    void parse_mixedInput_returnsMixedQuery() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("config ext:json");
        assertEquals(QueryType.MIXED, q.type());
        assertEquals("config", q.value());
        assertEquals("json", q.filters().get("ext"));
    }
    
    @Test
    void parse_duplicateContentQualifier_combinesWithAnd() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("content:hello content:world");
        assertEquals(QueryType.METADATA, q.type());
        assertEquals("hello AND world", q.filters().get("content"));
    }

    @Test
    void parse_duplicatePathQualifier_combinesWithAnd() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("path:A path:B");
        assertEquals(QueryType.METADATA, q.type());
        assertEquals("A AND B", q.filters().get("path"));
    }

    @Test
    void parse_duplicateExtQualifier_combinesWithAnd() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("ext:java ext:kt");
        assertEquals(QueryType.METADATA, q.type());
        assertEquals("java AND kt", q.filters().get("ext"));
    }

    @Test
    void parse_singleQualifier_isNotAndJoined() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("content:hello");
        assertEquals("hello", q.filters().get("content"),
                "Single qualifier value should not have AND appended");
    }

    @Test
    void parse_qualifierOrder_doesNotAffectResult() {
        QueryParser parser = new QueryParser();
        Query q1 = parser.parse("path:Documents content:report");
        Query q2 = parser.parse("content:report path:Documents");

        assertEquals(q1.type(), q2.type());
        assertEquals(q1.value(), q2.value());
        assertEquals(q1.filters().get("path"), q2.filters().get("path"));
        assertEquals(q1.filters().get("content"), q2.filters().get("content"));
    }

    @Test
    void parse_threeQualifiersAnyOrder_producesSameFilters() {
        QueryParser parser = new QueryParser();
        Query q1 = parser.parse("ext:java path:src modified:2024-01-01");
        Query q2 = parser.parse("modified:2024-01-01 ext:java path:src");
        Query q3 = parser.parse("path:src modified:2024-01-01 ext:java");

        assertEquals(q1.filters(), q2.filters());
        assertEquals(q1.filters(), q3.filters());
    }

    @Test
    void parse_pathQualifierWithSlash_preservesFullPathToken() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("path:src/main content:todo");

        assertEquals(QueryType.METADATA, q.type());
        assertEquals("src/main", q.filters().get("path"));
        assertEquals("todo", q.filters().get("content"));
    }
}