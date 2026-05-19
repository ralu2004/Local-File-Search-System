package app.search.query.preprocessor;

import app.search.query.Query;
import app.search.query.QueryType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SynonymDecorator}.
 * <p>
 * The decorator expands recognised shorthand terms in the free-text value
 * to OR-joined groups (e.g., {@code "img"} → {@code "(img OR image OR photo)"}),
 * while leaving filter values, query type, and unrecognised terms untouched.
 */
class SynonymDecoratorTest {

    private final QueryDecorator identity = new IdentityDecorator();

    /** Decorator with the same synonym map as {@link app.search.SearchEngine}. */
    private final SynonymDecorator decorator = new SynonymDecorator(
            identity,
            Map.of(
                    "img",  List.of("img", "image", "photo", "picture"),
                    "doc",  List.of("doc", "document", "docx", "pdf"),
                    "vid",  List.of("vid", "video", "mp4", "mov")
            )
    );

    private Query fulltext(String value) {
        return new Query(QueryType.FULLTEXT, value, Map.of());
    }

    private Query mixed(String value, Map<String, String> filters) {
        return new Query(QueryType.MIXED, value, filters);
    }

    @Test
    void nullValue_passesThrough() {
        Query input = new Query(QueryType.METADATA, null, Map.of("ext", "png"));
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
    void knownShorthand_img_isExpanded() {
        Query result = decorator.decorate(fulltext("img"));
        String expanded = result.value();
        assertTrue(expanded.contains("img"),     "Expansion must include 'img'");
        assertTrue(expanded.contains("image"),   "Expansion must include 'image'");
        assertTrue(expanded.contains("photo"),   "Expansion must include 'photo'");
        assertTrue(expanded.contains("picture"), "Expansion must include 'picture'");
        assertTrue(expanded.contains("OR"),      "Expansion must use OR operator");
        assertTrue(expanded.startsWith("(") && expanded.endsWith(")"),
                "Expansion must be wrapped in parentheses, got: " + expanded);
    }

    @Test
    void knownShorthand_doc_isExpanded() {
        Query result = decorator.decorate(fulltext("doc"));
        String expanded = result.value();
        assertTrue(expanded.contains("document"), "Expansion must include 'document'");
        assertTrue(expanded.contains("docx"),     "Expansion must include 'docx'");
        assertTrue(expanded.contains("pdf"),      "Expansion must include 'pdf'");
    }

    @Test
    void knownShorthand_vid_isExpanded() {
        Query result = decorator.decorate(fulltext("vid"));
        String expanded = result.value();
        assertTrue(expanded.contains("video"), "Expansion must include 'video'");
        assertTrue(expanded.contains("mp4"),   "Expansion must include 'mp4'");
        assertTrue(expanded.contains("mov"),   "Expansion must include 'mov'");
    }

    @Test
    void expansion_isCaseInsensitiveOnInput() {
        Query lower = decorator.decorate(fulltext("img"));
        Query upper = decorator.decorate(fulltext("IMG"));
        Query mixed = decorator.decorate(fulltext("Img"));

        // all three should expand
        assertTrue(upper.value().contains("OR"),
                "Uppercase 'IMG' should also be expanded");
        assertTrue(mixed.value().contains("OR"),
                "Mixed-case 'Img' should also be expanded");
    }

    @Test
    void unknownToken_passesThrough() {
        Query result = decorator.decorate(fulltext("hello"));
        assertEquals("hello", result.value(),
                "Unknown terms must be passed through unchanged");
    }

    @Test
    void mixedKnownAndUnknownTerms_onlyKnownAreExpanded() {
        Query result = decorator.decorate(fulltext("find img file"));
        String value = result.value();

        // "img" must be expanded
        assertTrue(value.contains("image"), "'img' must be expanded to include 'image'");
        // "find" and "file" must stay the same
        assertTrue(value.contains("find"), "'find' must remain as-is");
        assertTrue(value.contains("file"), "'file' must remain as-is");
    }

    @Test
    void multipleKnownShorthands_allExpanded() {
        Query result = decorator.decorate(fulltext("img doc"));
        String value = result.value();

        assertTrue(value.contains("image"),    "img → must expand to include 'image'");
        assertTrue(value.contains("document"), "doc → must expand to include 'document'");
    }

    @Test
    void tokenOrder_isPreserved() {
        Query result = decorator.decorate(fulltext("find img"));
        String value = result.value();

        int findIndex  = value.indexOf("find");
        int imageIndex = value.indexOf("image");
        assertTrue(findIndex < imageIndex,
                "'find' must appear before the expanded 'img' group");
    }

    @Test
    void filterValues_areNotExpanded() {
        // the filter key "ext" with value "img" must not be synonym-expanded
        Query input = mixed("find", Map.of("ext", "img"));
        Query result = decorator.decorate(input);
        assertEquals("img", result.filters().get("ext"),
                "Filter values must not be synonym-expanded — only the free-text value is");
    }

    @Test
    void queryType_isPreserved() {
        Query result = decorator.decorate(new Query(QueryType.MIXED, "img", Map.of("ext", "png")));
        assertEquals(QueryType.MIXED, result.type());
    }

    @Test
    void allFilters_arePassedThrough() {
        Map<String, String> filters = Map.of("ext", "png", "modified", "2025-01-01");
        Query result = decorator.decorate(mixed("img", filters));
        assertEquals(filters, result.filters());
    }

    @Test
    void emptyExpansionList_doesNotProduceMalformedOutput() {
        SynonymDecorator noExpansions = new SynonymDecorator(identity, Map.of("test", List.of()));
        Query result = noExpansions.decorate(fulltext("test"));
        assertNotNull(result.value());
    }
}
