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
}

