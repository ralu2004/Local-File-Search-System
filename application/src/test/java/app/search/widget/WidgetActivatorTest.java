package app.search.widget;

import app.model.RankedSearchResult;
import app.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WidgetActivator} as a factory:
 * null/empty guards, rule composition, ordering guarantees,
 * and custom-rule injection.
 */
class WidgetActivatorTest {

    private static RankedSearchResult result(String dir, String filename, String extension) {
        Path path = Path.of(dir, filename + "." + extension);
        SearchResult sr = new SearchResult(path, filename + "." + extension, extension, "", LocalDateTime.now(), 100L);
        return new RankedSearchResult(sr, List.of());
    }

    private static RankedSearchResult result(String extension) {
        return result("/home/user/docs", "file", extension);
    }

    private static boolean containsId(List<Widget> widgets, String id) {
        return widgets.stream().anyMatch(w -> id.equals(w.id()));
    }

    private static int indexOfId(List<Widget> widgets, String id) {
        for (int i = 0; i < widgets.size(); i++) {
            if (id.equals(widgets.get(i).id())) return i;
        }
        return -1;
    }

    @Test
    void nullResults_returnsEmptyList() {
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("hello", null);
        assertNotNull(widgets);
        assertTrue(widgets.isEmpty());
    }

    @Test
    void emptyResults_returnsEmptyList() {
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("hello", List.of());
        assertNotNull(widgets);
        assertTrue(widgets.isEmpty());
    }

    @Test
    void exportWidget_isAlwaysLast() {
        List<RankedSearchResult> results = List.of(result("png"), result("png"), result("jpg"));
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("color:red", results);
        assertFalse(widgets.isEmpty());
        assertEquals("export-list", widgets.getLast().id(),
                "export-list widget must always appear last");
    }

    @Test
    void galleryWidget_appearsBeforeExport_whenActivated() {
        List<RankedSearchResult> results = List.of(result("png"), result("jpg"), result("webp"));
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("", results);

        int galleryIdx = indexOfId(widgets, "gallery");
        int exportIdx  = indexOfId(widgets, "export-list");
        assertTrue(galleryIdx >= 0,  "gallery widget must be present");
        assertTrue(exportIdx  >= 0,  "export-list widget must be present");
        assertTrue(galleryIdx < exportIdx, "gallery must appear before export-list");
    }

    @Test
    void imageResultsInSameDir_activatesBothGalleryAndFolderWidgets() {
        List<RankedSearchResult> results = List.of(
                result("/home/user/photos", "a", "png"),
                result("/home/user/photos", "b", "jpg")
        );
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("", results);
        assertTrue(containsId(widgets, "gallery"),     "gallery must fire for image results");
        assertTrue(containsId(widgets, "open-folder"), "open-folder must fire for same-dir results");
    }

    @Test
    void contentQualifier_activatesMarkerAlongsideOtherRules() {
        List<RankedSearchResult> results = List.of(result("txt"), result("txt"));
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("content:hello", results);
        assertTrue(containsId(widgets, "search-content"), "content marker must be present");
        assertTrue(containsId(widgets, "export-list"),    "export-list must always be present");
    }

    @Test
    void customRule_isInvokedByActivator() {
        Widget customWidget = new Widget("custom-rule", "Custom Widget", "marker");
        WidgetActivationRule customRule = (q, results) -> Optional.of(customWidget);

        WidgetActivator activator = new WidgetActivator(List.of(customRule));
        List<Widget> widgets = activator.activate("anything", List.of(result("txt")));

        assertEquals(1, widgets.size());
        assertEquals("custom-rule", widgets.getFirst().id());
    }

    @Test
    void customRuleReturningEmpty_producesNoWidget() {
        WidgetActivationRule neverFires = (q, results) -> Optional.empty();
        WidgetActivator activator = new WidgetActivator(List.of(neverFires));
        List<Widget> widgets = activator.activate("anything", List.of(result("txt")));
        assertTrue(widgets.isEmpty());
    }

    @Test
    void activatorWithNoRules_returnsEmptyList() {
        WidgetActivator activator = new WidgetActivator(List.of());
        List<Widget> widgets = activator.activate("anything", List.of(result("txt")));
        assertTrue(widgets.isEmpty());
    }

    @Test
    void customRulesAreEvaluatedInRegistrationOrder() {
        Widget first  = new Widget("first",  "First",  "marker");
        Widget second = new Widget("second", "Second", "marker");
        WidgetActivator activator = new WidgetActivator(List.of(
                (q, r) -> Optional.of(first),
                (q, r) -> Optional.of(second)
        ));
        List<Widget> widgets = activator.activate("", List.of(result("txt")));
        assertEquals(2, widgets.size());
        assertEquals("first",  widgets.get(0).id());
        assertEquals("second", widgets.get(1).id());
    }

    @Test
    void anyNonEmptyResults_alwaysProduceAtLeastExportWidget() {
        List<RankedSearchResult> results = List.of(result("xyz")); // unknown extension
        List<Widget> widgets = WidgetActivator.withDefaultRules().activate("some query", results);
        assertTrue(containsId(widgets, "export-list"),
                "export-list must always be present for any non-empty result set");
    }

    @Test
    void defaultRules_expectedRuleCountIsRegistered() {
        assertEquals(7, WidgetActivator.defaultRules().size(),
                "Default rule set must contain exactly 7 strategies");
    }
}