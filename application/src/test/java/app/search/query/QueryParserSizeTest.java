package app.search.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies size filter parsing for bytes and binary units (KB/MB/GB).
 */
class QueryParserSizeTest {

    @Test
    void sizeFilter_digitsAreTreatedAsBytes() {
        QueryParser parser = new QueryParser();
        Query q = parser.parse("size:500");
        assertEquals(QueryType.METADATA, q.type());
        assertNull(q.value());
        assertEquals("500", q.filters().get("size"));
    }

    @Test
    void sizeFilter_supportsBinaryUnits() {
        QueryParser parser = new QueryParser();

        Query qKb = parser.parse("size:10kb");
        assertEquals(String.valueOf(10L * 1024L), qKb.filters().get("size"));

        Query qMb = parser.parse("size:5MB");
        assertEquals(String.valueOf(5L * 1024L * 1024L), qMb.filters().get("size"));

        Query qGb = parser.parse("size:1gB");
        assertEquals(String.valueOf(1L * 1024L * 1024L * 1024L), qGb.filters().get("size"));
    }

    @Test
    void sizeFilter_rejectsInvalidValues() {
        QueryParser parser = new QueryParser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse("size:abc"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("size:10tb"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("size:-1mb"));
    }
}

