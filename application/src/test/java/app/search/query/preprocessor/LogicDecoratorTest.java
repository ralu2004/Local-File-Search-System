package app.search.query.preprocessor;

import app.search.query.Query;
import app.search.query.QueryType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LogicDecorator}.
 * <p>
 * The decorator rewrites the free-text value into a valid FTS5 expression:
 * <ul>
 *   <li>Plain alphanumeric tokens get a {@code *} wildcard appended to the <em>last</em> eligible one.</li>
 *   <li>FTS operators (AND, OR, NOT, NEAR) are uppercased and passed through without a wildcard.</li>
 *   <li>Tokens already ending with {@code *} are left unchanged.</li>
 *   <li>Special-character tokens (e.g., file.txt) are double-quote escaped.</li>
 *   <li>Quoted phrases are preserved as-is.</li>
 *   <li>Null and blank values pass through untouched.</li>
 * </ul>
 */
class LogicDecoratorTest {

    private final LogicDecorator decorator = new LogicDecorator(new IdentityDecorator());

    private String decorate(String value) {
        Query input = new Query(QueryType.FULLTEXT, value, Map.of());
        return decorator.decorate(input).value();
    }

    @Test
    void nullValue_passesThrough() {
        Query input = new Query(QueryType.METADATA, null, Map.of("ext", "java"));
        Query result = decorator.decorate(input);
        assertNull(result.value());
    }

    @Test
    void blankValue_passesThrough() {
        Query input = new Query(QueryType.FULLTEXT, "   ", Map.of());
        Query result = decorator.decorate(input);
        assertEquals("   ", result.value());
    }

    @Test
    void singleAlphanumericToken_getsPrefixWildcard() {
        assertEquals("hello*", decorate("hello"));
    }

    @Test
    void singleDigitToken_getsPrefixWildcard() {
        assertEquals("123*", decorate("123"));
    }

    @Test
    void tokenAlreadyEndingWithWildcard_isNotDoubleWildcarded() {
        assertEquals("hello*", decorate("hello*"));
    }

    @Test
    void twoPlainTokens_onlyLastGetsWildcard() {
        String result = decorate("hello world");
        assertTrue(result.contains("hello"),  "First token must be present");
        assertFalse(result.contains("hello*"), "First token must NOT get wildcard");
        assertTrue(result.contains("world*"), "Last token must get wildcard");
    }

    @Test
    void threeTokens_onlyLastGetsWildcard() {
        String result = decorate("one two three");
        assertFalse(result.contains("one*"),  "Intermediate tokens must not have wildcard");
        assertFalse(result.contains("two*"),  "Intermediate tokens must not have wildcard");
        assertTrue(result.contains("three*"), "Only the last token gets the wildcard");
    }

    @Test
    void andOperator_isPassedThroughUppercaseNoWildcard() {
        String result = decorate("hello AND world");
        assertTrue(result.contains("AND"), "AND operator must appear uppercased");
        assertFalse(result.contains("AND*"), "AND must not receive a wildcard");
        assertTrue(result.contains("world*"), "Last eligible token must still get wildcard");
    }

    @Test
    void orOperator_isPassedThroughUppercaseNoWildcard() {
        String result = decorate("cat OR dog");
        assertTrue(result.contains("OR"),   "OR operator must appear uppercased");
        assertFalse(result.contains("OR*"), "OR must not receive a wildcard");
        assertTrue(result.contains("dog*"), "Last token must get wildcard");
    }

    @Test
    void notOperator_isPassedThroughUppercaseNoWildcard() {
        String result = decorate("hello NOT world");
        assertTrue(result.contains("NOT"),   "NOT operator must appear uppercased");
        assertFalse(result.contains("NOT*"), "NOT must not receive a wildcard");
        assertTrue(result.contains("world*"), "Last token must get wildcard");
    }

    @Test
    void nearOperator_isPassedThroughUppercaseNoWildcard() {
        String result = decorate("hello NEAR world");
        assertTrue(result.contains("NEAR"),   "NEAR operator must appear uppercased");
        assertFalse(result.contains("NEAR*"), "NEAR must not receive a wildcard");
        assertTrue(result.contains("world*"), "Last token must get wildcard");
    }

    @Test
    void lowercaseFtsOperator_isNormalisedToUppercase() {
        String result = decorate("hello and world");
        assertTrue(result.contains("AND"), "'and' must be normalised to uppercase AND");
    }

    @Test
    void queryEndingWithFtsOperator_operatorGetsNoWildcard() {
        // "hello AND" — AND is the last token but must not get a wildcard
        String result = decorate("hello AND");
        assertFalse(result.contains("AND*"), "FTS operator as last token must not get wildcard");
    }

    @Test
    void dotInToken_causesAutoQuoting() {
        // "file.txt" contains a dot — not a pure alphanumeric token → must be quoted
        String result = decorate("file.txt");
        assertTrue(result.contains("\""), "Dot-containing token must be double-quote escaped");
        assertTrue(result.contains("file.txt"), "Token content must be preserved inside quotes");
    }

    @Test
    void hyphenInToken_causesAutoQuoting() {
        String result = decorate("well-known");
        assertTrue(result.contains("\""), "Hyphen-containing token must be double-quote escaped");
    }

    @Test
    void autoQuotedToken_isLastAndDoesNotGetWildcard() {
        // special-char tokens are quoted; they are not prefix-wildcard candidates
        String result = decorate("file.txt");
        assertFalse(result.contains("\"file.txt\"*"),
                "Auto-quoted tokens must not have a trailing wildcard");
    }

    @Test
    void quotedPhrase_isPreservedVerbatim() {
        String result = decorate("\"hello world\"");
        assertTrue(result.contains("\"hello world\""),
                "Quoted phrase must be preserved as-is");
    }

    @Test
    void quotedPhrase_doesNotGetPrefixWildcard() {
        String result = decorate("\"hello world\"");
        assertFalse(result.contains("world\"*") || result.contains("\"*"),
                "Quoted phrase tokens must not get a wildcard appended");
    }

    @Test
    void termBeforeQuotedPhrase_doesNotGetWildcard() {
        // if the last eligible token is inside quotes, the pre-phrase term is not the last candidate
        String result = decorate("find \"hello world\"");
        assertTrue(result.contains("find*"),
                "'find' must get the wildcard");
    }

    @Test
    void termAfterQuotedPhrase_getsWildcard() {
        String result = decorate("\"hello world\" after");
        assertTrue(result.contains("after*"),
                "Token after a quoted phrase is the last candidate and must get wildcard");
    }

    @Test
    void existingWildcard_isNotDuplicated() {
        String result = decorate("config*");
        assertEquals(1, countOccurrences(result, "config*"),
                "Wildcard must appear exactly once");
    }

    @Test
    void existingWildcardNotLastToken_doesNotPreventWildcardOnActualLastToken() {
        // "pre* final" — "pre*" already has wildcard, "final" is the last eligible token
        String result = decorate("pre* final");
        assertTrue(result.contains("final*"),
                "Last eligible token must still get wildcard even if an earlier token already has one");
    }

    @Test
    void queryType_isPreserved() {
        Query input = new Query(QueryType.MIXED, "config", Map.of("ext", "json"));
        Query result = decorator.decorate(input);
        assertEquals(QueryType.MIXED, result.type());
    }

    @Test
    void filters_arePassedThroughUnchanged() {
        Map<String, String> filters = Map.of("ext", "java", "modified", "2025-01-01");
        Query input = new Query(QueryType.MIXED, "hello", filters);
        Query result = decorator.decorate(input);
        assertEquals(filters, result.filters());
    }

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
