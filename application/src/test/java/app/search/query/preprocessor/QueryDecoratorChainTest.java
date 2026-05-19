package app.search.query.preprocessor;

import app.search.query.Query;
import app.search.query.QueryType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link QueryDecorator} chain composition, invocation order,
 * and end-to-end pipeline behaviour (Sanitization → Synonym → Logic).
 * <p>
 * The Sanitization → Synonym → Logic ordering matters: sanitize before
 * expanding synonyms so the synonym table never sees FTS-breaking chars,
 * and expand before Logic so the wildcard is applied to the last
 * post-expansion token rather than the raw shorthand.
 */
class QueryDecoratorChainTest {

    @Test
    void identityDecorator_returnsQueryUnchanged() {
        Query input = new Query(QueryType.FULLTEXT, "hello", Map.of("ext", "java"));
        Query result = new IdentityDecorator().decorate(input);
        assertSame(input, result, "IdentityDecorator must return the exact same instance");
    }

    @Test
    void chainInvocationOrder_isOuterToInner() {
        List<String> log = new ArrayList<>();

        QueryDecorator inner = query -> {
            log.add("inner");
            return query;
        };
        QueryDecorator outer = query -> {
            log.add("outer");
            return inner.decorate(query);
        };

        Query input = new Query(QueryType.FULLTEXT, "test", Map.of());
        outer.decorate(input);

        assertEquals(List.of("outer", "inner"), log,
                "Decorators must execute outer-first (wrapping order, not reverse)");
    }

    @Test
    void chainCanBeComposedArbitrarilyDeep() {
        // build 5-level chain: d5(d4(d3(d2(d1(identity)))))
        QueryDecorator chain = new IdentityDecorator();
        for (int i = 0; i < 5; i++) {
            final QueryDecorator prev = chain;
            chain = query -> prev.decorate(new Query(query.type(), query.value() + "x", query.filters()));
        }
        Query result = chain.decorate(new Query(QueryType.FULLTEXT, "", Map.of()));
        assertEquals("xxxxx", result.value(),
                "Each decorator in the chain must have had a chance to transform the query");
    }

    /**
     * Builds the same decorator chain as {@link app.search.SearchEngine}.
     */
    private QueryDecorator buildPipeline() {
        return new SanitizationDecorator(
                new SynonymDecorator(
                        new LogicDecorator(new IdentityDecorator()),
                        Map.of("img", List.of("img", "image", "photo", "picture"))
                )
        );
    }

    @Test
    void pipeline_plainTerm_getsSanitizedAndPrefixWildcarded() {
        Query result = buildPipeline().decorate(new Query(QueryType.FULLTEXT, "readme", Map.of()));
        String value = result.value();
        assertTrue(value.contains("readme*"), "Plain term must pass through to get prefix wildcard");
        assertFalse(value.contains("'"),      "Sanitizer must have run before Logic");
    }

    @Test
    void pipeline_dangerousChars_areStrippedBeforeExpansionAndLogic() {
        // "it's" → sanitize → "its" → no synonym → logic → "its*"
        Query result = buildPipeline().decorate(new Query(QueryType.FULLTEXT, "it's", Map.of()));
        assertEquals("its*", result.value(),
                "Single-quote must be stripped before synonym lookup and wildcard addition");
    }

    @Test
    void pipeline_synonym_isExpandedThenWildcardedOnLastToken() {
        Query result = buildPipeline().decorate(new Query(QueryType.FULLTEXT, "img", Map.of()));
        String value = result.value();

        assertTrue(value.contains("image"),   "Synonym 'image' must be present after expansion");
        assertTrue(value.contains("photo"),   "Synonym 'photo' must be present after expansion");
        assertTrue(value.contains("picture"), "Synonym 'picture' must be present after expansion");
        assertTrue(value.contains("*"), "Logic must append a wildcard after synonym expansion");
    }

    @Test
    void pipeline_ftsOperatorsFromExpansion_areNotWildcarded() {
        Query result = buildPipeline().decorate(new Query(QueryType.FULLTEXT, "img", Map.of()));
        String value = result.value();
        assertFalse(value.contains("OR*"),  "OR from synonym expansion must not be wildcarded");
    }

    @Test
    void pipeline_multipleTermsWithSynonym_onlyLastTermWildcarded() {
        // "find img" → sanitize → "find img" → synonym → "find (img OR image OR photo OR picture)"
        // logic wildcards the last eligible plain token inside the group
        Query result = buildPipeline().decorate(new Query(QueryType.FULLTEXT, "find img", Map.of()));
        String value = result.value();

        assertTrue(value.contains("find"),  "'find' must be present");
        assertFalse(value.contains("find*"), "'find' is not the last eligible token and must not have a wildcard");
        assertTrue(value.contains("image"), "img synonym 'image' must be present");
        assertTrue(value.contains("*"),     "Wildcard must appear somewhere in the output");
    }

    @Test
    void pipeline_quotedPhrase_isPreservedEndToEnd() {
        // quoted phrases must pass all three decorators intact
        Query result = buildPipeline().decorate(new Query(QueryType.FULLTEXT, "\"hello world\"", Map.of()));
        String value = result.value();
        assertTrue(value.contains("\"hello world\""),
                "Quoted phrase must pass through sanitization, synonym, and logic unchanged");
    }

    @Test
    void pipeline_nullValue_passesAllDecoratorsWithoutError() {
        Query input = new Query(QueryType.METADATA, null, Map.of("ext", "java"));
        Query result = buildPipeline().decorate(input);
        assertNull(result.value(),
                "Null value must survive all decorators without modification or exception");
        assertEquals("java", result.filters().get("ext"),
                "Filters must survive null-value passthrough unchanged");
    }

    @Test
    void pipeline_queryType_isPreservedAcrossAllDecorators() {
        for (QueryType type : QueryType.values()) {
            Query input = new Query(type, "test", Map.of());
            Query result = buildPipeline().decorate(input);
            assertEquals(type, result.type(),
                    "QueryType." + type + " must not be altered by any decorator in the pipeline");
        }
    }

    @Test
    void pipeline_filters_arePreservedAcrossAllDecorators() {
        Map<String, String> filters = Map.of("ext", "java", "modified", "2025-01-01", "path", "src/main");
        Query input = new Query(QueryType.MIXED, "config", filters);
        Query result = buildPipeline().decorate(input);
        assertEquals(filters, result.filters(),
                "All filter entries must survive the full decorator pipeline unchanged");
    }
}
