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
 * Unit tests for each {@link WidgetActivationRule} implementation in
 * {@link WidgetActivationRules}. Each rule is tested in isolation so its
 * activation condition can be verified without constructing a full activator.
 */
class WidgetActivationRulesTest {

    private static RankedSearchResult result(String dir, String filename, String extension) {
        Path path = Path.of(dir, filename + "." + extension);
        SearchResult sr = new SearchResult(path, filename + "." + extension, extension, "", LocalDateTime.now(), 100L);
        return new RankedSearchResult(sr, List.of());
    }

    private static RankedSearchResult result(String extension) {
        return result("/home/user/docs", "file", extension);
    }

    @Test
    void majorityImage_allImages_activatesGallery() {
        List<RankedSearchResult> results = List.of(result("png"), result("jpg"), result("webp"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityImageRule().evaluate("", results);
        assertTrue(widget.isPresent());
        assertEquals("gallery", widget.get().id());
        assertEquals("action",  widget.get().type());
    }

    @Test
    void majorityImage_exactlyHalfImages_activatesGallery() {
        // threshold is 0.5 (>=), so exactly half qualifies
        List<RankedSearchResult> results = List.of(result("png"), result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityImageRule().evaluate("", results);
        assertTrue(widget.isPresent(), "Exactly 50% images must activate the gallery widget");
    }

    @Test
    void majorityImage_mostlyText_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("png"), result("txt"), result("java"), result("md"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityImageRule().evaluate("", results);
        assertFalse(widget.isPresent(), "Less than 50% images must not activate gallery");
    }

    @Test
    void majorityImage_noImages_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("txt"), result("java"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityImageRule().evaluate("", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void majorityImage_variousImageExtensions_allCountedTogether() {
        List<RankedSearchResult> results = List.of(
                result("png"), result("jpg"), result("jpeg"), result("gif"), result("bmp"), result("webp"),
                result("txt")
        );
        Optional<Widget> widget = new WidgetActivationRules.MajorityImageRule().evaluate("", results);
        assertTrue(widget.isPresent(), "All image extension types must count toward gallery threshold");
    }

    @Test
    void majorityLog_allLogs_activatesMarker() {
        List<RankedSearchResult> results = List.of(result("log"), result("log"), result("log"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityLogRule().evaluate("", results);
        assertTrue(widget.isPresent());
        assertEquals("analyze-logs", widget.get().id());
        assertEquals("marker", widget.get().type());
    }

    @Test
    void majorityLog_fewLogs_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("log"), result("txt"), result("java"), result("md"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityLogRule().evaluate("", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void majorityLog_noLogs_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("txt"), result("java"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityLogRule().evaluate("", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void majorityMarkdown_mdFiles_activatesMarker() {
        List<RankedSearchResult> results = List.of(result("md"), result("md"), result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityMarkdownRule().evaluate("", results);
        assertTrue(widget.isPresent());
        assertEquals("markdown-preview", widget.get().id());
    }

    @Test
    void majorityMarkdown_markdownExtension_alsoCounts() {
        List<RankedSearchResult> results = List.of(result("markdown"), result("markdown"), result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityMarkdownRule().evaluate("", results);
        assertTrue(widget.isPresent(), ".markdown extension must count alongside .md");
    }

    @Test
    void majorityMarkdown_mixedMdAndMarkdown_combinedCount() {
        List<RankedSearchResult> results = List.of(result("md"), result("markdown"), result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityMarkdownRule().evaluate("", results);
        assertTrue(widget.isPresent(), ".md and .markdown counts must be combined");
    }

    @Test
    void majorityMarkdown_noMarkdown_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("txt"), result("java"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityMarkdownRule().evaluate("", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void majorityDiff_diffFiles_activatesMarker() {
        List<RankedSearchResult> results = List.of(result("diff"), result("diff"), result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityDiffRule().evaluate("", results);
        assertTrue(widget.isPresent());
        assertEquals("diff-view", widget.get().id());
    }

    @Test
    void majorityDiff_patchFiles_alsoCounted() {
        List<RankedSearchResult> results = List.of(result("patch"), result("patch"), result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityDiffRule().evaluate("", results);
        assertTrue(widget.isPresent(), ".patch extension must count toward diff threshold");
    }

    @Test
    void majorityDiff_noDiffs_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("txt"), result("java"));
        Optional<Widget> widget = new WidgetActivationRules.MajorityDiffRule().evaluate("", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void sameDirectory_allInSameDir_activatesAction() {
        List<RankedSearchResult> results = List.of(
                result("/home/user/docs", "a", "txt"),
                result("/home/user/docs", "b", "txt"),
                result("/home/user/docs", "c", "java")
        );
        Optional<Widget> widget = new WidgetActivationRules.SameDirectoryRule().evaluate("", results);
        assertTrue(widget.isPresent());
        assertEquals("open-folder", widget.get().id());
        assertEquals("action", widget.get().type());
    }

    @Test
    void sameDirectory_singleResult_activatesAction() {
        List<RankedSearchResult> results = List.of(result("/home/user/docs", "only", "txt"));
        Optional<Widget> widget = new WidgetActivationRules.SameDirectoryRule().evaluate("", results);
        assertTrue(widget.isPresent(), "A single result is trivially in the same directory");
    }

    @Test
    void sameDirectory_differentDirs_doesNotActivate() {
        List<RankedSearchResult> results = List.of(
                result("/home/user/docs", "a", "txt"),
                result("/home/user/src",  "b", "java")
        );
        Optional<Widget> widget = new WidgetActivationRules.SameDirectoryRule().evaluate("", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void contentQualifier_queryContainsContentPrefix_activatesMarker() {
        List<RankedSearchResult> results = List.of(result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.ContentQualifierRule()
                .evaluate("content:hello", results);
        assertTrue(widget.isPresent());
        assertEquals("search-content", widget.get().id());
        assertEquals("marker", widget.get().type());
    }

    @Test
    void contentQualifier_queryWithoutContentPrefix_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.ContentQualifierRule()
                .evaluate("hello world", results);
        assertFalse(widget.isPresent());
    }

    @Test
    void contentQualifier_nullQuery_doesNotActivate() {
        List<RankedSearchResult> results = List.of(result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.ContentQualifierRule()
                .evaluate(null, results);
        assertFalse(widget.isPresent());
    }

    @Test
    void alwaysPresent_nonEmptyResults_activatesExport() {
        List<RankedSearchResult> results = List.of(result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.AlwaysPresentRule()
                .evaluate("anything", results);
        assertTrue(widget.isPresent());
        assertEquals("export-list", widget.get().id());
        assertEquals("action", widget.get().type());
    }

    @Test
    void alwaysPresent_emptyQuery_stillActivates() {
        List<RankedSearchResult> results = List.of(result("txt"));
        Optional<Widget> widget = new WidgetActivationRules.AlwaysPresentRule()
                .evaluate("", results);
        assertTrue(widget.isPresent(), "AlwaysPresentRule must activate regardless of query content");
    }
}
