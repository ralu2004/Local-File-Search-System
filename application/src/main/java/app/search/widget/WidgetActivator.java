package app.search.widget;

import app.model.RankedSearchResult;
import app.util.FileTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that analyzes a search result set and activates relevant context-aware widgets.
 * <p>
 * Uses rules based on file extension distribution, path patterns, and query content
 * to decide which widgets are relevant for the current result set.
 */
public class WidgetActivator {

    private static final double MAJORITY_THRESHOLD = 0.5;

    private WidgetActivator() {}

    /**
     * Analyzes the query and result set and returns the list of widgets to activate.
     *
     * @param query   the raw user query
     * @param results the current search result set
     * @return list of activated widgets, empty if none are relevant
     */
    public static List<Widget> activate(String query, List<RankedSearchResult> results) {
        List<Widget> widgets = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            return widgets;
        }

        Map<String, Long> extensionCounts = buildExtensionCounts(results);
        long total = results.size();

        if (isMajorityImages(extensionCounts, total)) {
            widgets.add(new Widget("gallery", "View as Gallery", "action"));
        }
        if (isMajorityLogs(extensionCounts, total)) {
            widgets.add(new Widget("analyze-logs", "Analyze Logs", "marker"));
        }
        if (isMajorityMarkdown(extensionCounts, total)) {
            widgets.add(new Widget("markdown-preview", "Markdown Results", "marker"));
        }
        if (isMajorityDiffs(extensionCounts, total)) {
            widgets.add(new Widget("diff-view", "Diff Results", "marker"));
        }
        if (allInSameDirectory(results)) {
            widgets.add(new Widget("open-folder", "Copy Folder Path", "action"));
        }
        if (hasContentQualifier(query)) {
            widgets.add(new Widget("search-content", "Content Search Active", "marker"));
        }

        widgets.add(new Widget("export-list", "Export File List", "action"));

        return widgets;
    }

    private static Map<String, Long> buildExtensionCounts(List<RankedSearchResult> results) {
        return results.stream()
                .map(r -> r.result().extension() == null ? "" : r.result().extension().toLowerCase())
                .collect(Collectors.groupingBy(ext -> ext, Collectors.counting()));
    }

    private static boolean isMajorityImages(Map<String, Long> counts, long total) {
        long imageCount = counts.entrySet().stream()
                .filter(e -> FileTypes.IMAGE_EXTENSIONS.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        return (double) imageCount / total >= MAJORITY_THRESHOLD;
    }

    private static boolean isMajorityLogs(Map<String, Long> counts, long total) {
        return (double) counts.getOrDefault("log", 0L) / total >= MAJORITY_THRESHOLD;
    }

    private static boolean isMajorityMarkdown(Map<String, Long> counts, long total) {
        long mdCount = counts.getOrDefault("md", 0L) + counts.getOrDefault("markdown", 0L);
        return (double) mdCount / total >= MAJORITY_THRESHOLD;
    }

    private static boolean isMajorityDiffs(Map<String, Long> counts, long total) {
        long diffCount = counts.getOrDefault("patch", 0L) + counts.getOrDefault("diff", 0L);
        return (double) diffCount / total >= MAJORITY_THRESHOLD;
    }

    private static boolean allInSameDirectory(List<RankedSearchResult> results) {
        return results.stream()
                .map(r -> r.result().path().getParent())
                .distinct()
                .count() == 1;
    }

    private static boolean hasContentQualifier(String query) {
        return query != null && query.contains("content:");
    }
}