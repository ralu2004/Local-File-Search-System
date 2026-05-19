package app.search.query.preprocessor;

import app.search.query.Query;
import app.search.query.QueryType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SanitizationDecorator}.
 * <p>
 * The decorator strips FTS5-breaking characters ({@code '}, {@code ;}, {@code \})
 * from the free-text term and normalises internal whitespace. Filter values and
 * query type must pass through unchanged.
 */
class SanitizationDecoratorTest {

    /** Terminal decorator that returns queries unchanged — used as the chain base. */
    private final QueryDecorator identity = new IdentityDecorator();
    private final SanitizationDecorator decorator = new SanitizationDecorator(identity);

    private Query fulltext(String value) {
        return new Query(QueryType.FULLTEXT, value, Map.of());
    }

    private Query mixed(String value, Map<String, String> filters) {
        return new Query(QueryType.MIXED, value, filters);
    }

    @Test
    void nullValue_passesThrough() {
        Query input = new Query(QueryType.METADATA, null, Map.of("ext", "java"));
        Query result = decorator.decorate(input);
        assertNull(result.value());
        assertEquals("java", result.filters().get("ext"));
    }

    @Test
    void blankValue_passesThrough() {
        Query input = new Query(QueryType.FULLTEXT, "   ", Map.of());
        Query result = decorator.decorate(input);
        assertEquals("   ", result.value(), "Blank value must not be modified — it is not null");
    }

    @Test
    void singleQuote_isStripped() {
        Query result = decorator.decorate(fulltext("it's"));
        assertEquals("its", result.value());
    }

    @Test
    void multipleSingleQuotes_areAllStripped() {
        Query result = decorator.decorate(fulltext("don't can't won't"));
        assertEquals("dont cant wont", result.value());
    }

    @Test
    void semicolon_isStripped() {
        Query result = decorator.decorate(fulltext("hello;world"));
        assertEquals("helloworld", result.value());
    }

    @Test
    void semicolonSurroundedBySpaces_isStripped() {
        Query result = decorator.decorate(fulltext("hello ; world"));
        assertEquals("hello world", result.value());
    }

    @Test
    void backslash_isStripped() {
        Query result = decorator.decorate(fulltext("path\\to\\file"));
        assertEquals("pathtofile", result.value());
    }

    @Test
    void allThreeFtsDangerousChars_areAllStripped() {
        Query result = decorator.decorate(fulltext("it's;here\\now"));
        assertEquals("itsherenow", result.value(),
                "Single-quote, semicolon, and backslash must all be stripped");
    }

    @Test
    void leadingAndTrailingWhitespace_isTrimmed() {
        Query result = decorator.decorate(fulltext("  hello world  "));
        assertEquals("hello world", result.value());
    }

    @Test
    void internalMultipleSpaces_areCollapsedToOne() {
        Query result = decorator.decorate(fulltext("hello   world"));
        assertEquals("hello world", result.value());
    }

    @Test
    void tabsAndNewlines_areNormalisedToSingleSpace() {
        Query result = decorator.decorate(fulltext("hello\t\nworld"));
        assertEquals("hello world", result.value());
    }

    @Test
    void cleanAlphanumericInput_isUnchanged() {
        Query result = decorator.decorate(fulltext("hello world 123"));
        assertEquals("hello world 123", result.value());
    }

    @Test
    void ftsOperators_areNotStripped() {
        // AND / OR / NOT are FTS keywords — the decorator must not strip them
        Query result = decorator.decorate(fulltext("hello AND world OR NOT test"));
        assertEquals("hello AND world OR NOT test", result.value());
    }

    @Test
    void doubleQuotes_areNotStripped() {
        // double-quotes form FTS phrase queries and must not be removed
        Query result = decorator.decorate(fulltext("\"hello world\""));
        assertEquals("\"hello world\"", result.value());
    }

    @Test
    void queryType_isPreserved() {
        Query input = new Query(QueryType.MIXED, "config; ext:json", Map.of("ext", "json"));
        Query result = decorator.decorate(input);
        assertEquals(QueryType.MIXED, result.type());
    }

    @Test
    void filterValues_areNotSanitised() {
        // sanitisation only touches the free-text value, never filter values.
        Query input = mixed("notes", Map.of("path", "src;main", "ext", "java"));
        Query result = decorator.decorate(input);
        assertEquals("src;main", result.filters().get("path"),
                "Filter values must not be modified by SanitizationDecorator");
        assertEquals("java", result.filters().get("ext"));
    }

    @Test
    void filters_arePassedThroughUnchanged() {
        Map<String, String> filters = Map.of("ext", "java", "modified", "2025-01-01");
        Query input = mixed("test", filters);
        Query result = decorator.decorate(input);
        assertEquals(filters, result.filters());
    }
}
