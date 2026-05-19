package app.search.widget;

import app.model.RankedSearchResult;
import app.util.FileTypes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Built-in {@link WidgetActivationRule} implementations.
 * <p>
 * Each rule is a stateless, independently testable strategy. New rules
 * can be added here and registered in {@link WidgetActivator} without
 * touching any existing logic.
 *
 * <h2>Available rules</h2>
 * <ul>
 *   <li>{@link MajorityImageRule}     — activates the gallery widget</li>
 *   <li>{@link MajorityLogRule}       — activates the analyze-logs marker</li>
 *   <li>{@link MajorityMarkdownRule}  — activates the markdown-preview marker</li>
 *   <li>{@link MajorityDiffRule}      — activates the diff-view marker</li>
 *   <li>{@link SameDirectoryRule}     — activates the copy-folder-path action</li>
 *   <li>{@link ContentQualifierRule}  — activates the content-search-active marker</li>
 *   <li>{@link AlwaysPresentRule}     — activates the export-list action whenever results exist</li>
 * </ul>
 */
public final class WidgetActivationRules {

    private WidgetActivationRules() {}

    static Map<String, Long> extensionCounts(List<RankedSearchResult> results) {
        return results.stream()
                .map(r -> r.result().extension() == null ? "" : r.result().extension().toLowerCase())
                .collect(Collectors.groupingBy(ext -> ext, Collectors.counting()));
    }

    static boolean isMajority(long count, long total, double threshold) {
        return total > 0 && (double) count / total >= threshold;
    }

    /**
     * Activates the "View as Gallery" action when images make up at least
     * {@value #THRESHOLD} of the result set.
     */
    public static final class MajorityImageRule implements WidgetActivationRule {

        static final double THRESHOLD = 0.5;

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            Map<String, Long> counts = extensionCounts(results);
            long imageCount = counts.entrySet().stream()
                    .filter(e -> FileTypes.IMAGE_EXTENSIONS.contains(e.getKey()))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            if (isMajority(imageCount, results.size(), THRESHOLD)) {
                return Optional.of(new Widget("gallery", "View as Gallery", "action"));
            }
            return Optional.empty();
        }
    }

    /**
     * Activates the "Analyze Logs" marker when {@code .log} files make up at
     * least {@value #THRESHOLD} of the result set.
     */
    public static final class MajorityLogRule implements WidgetActivationRule {

        static final double THRESHOLD = 0.5;

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            Map<String, Long> counts = extensionCounts(results);
            long logCount = counts.getOrDefault("log", 0L);
            if (isMajority(logCount, results.size(), THRESHOLD)) {
                return Optional.of(new Widget("analyze-logs", "Analyze Logs", "marker"));
            }
            return Optional.empty();
        }
    }

    /**
     * Activates the "Markdown Results" marker when {@code .md} / {@code .markdown}
     * files make up at least {@value #THRESHOLD} of the result set.
     */
    public static final class MajorityMarkdownRule implements WidgetActivationRule {

        static final double THRESHOLD = 0.5;

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            Map<String, Long> counts = extensionCounts(results);
            long mdCount = counts.getOrDefault("md", 0L) + counts.getOrDefault("markdown", 0L);
            if (isMajority(mdCount, results.size(), THRESHOLD)) {
                return Optional.of(new Widget("markdown-preview", "Markdown Results", "marker"));
            }
            return Optional.empty();
        }
    }

    /**
     * Activates the "Diff Results" marker when {@code .diff} / {@code .patch}
     * files make up at least {@value #THRESHOLD} of the result set.
     */
    public static final class MajorityDiffRule implements WidgetActivationRule {

        static final double THRESHOLD = 0.5;

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            Map<String, Long> counts = extensionCounts(results);
            long diffCount = counts.getOrDefault("patch", 0L) + counts.getOrDefault("diff", 0L);
            if (isMajority(diffCount, results.size(), THRESHOLD)) {
                return Optional.of(new Widget("diff-view", "Diff Results", "marker"));
            }
            return Optional.empty();
        }
    }

    /**
     * Activates the "Copy Folder Path" action when all results reside in the
     * same parent directory.
     */
    public static final class SameDirectoryRule implements WidgetActivationRule {

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            boolean sameDir = results.stream()
                    .map(r -> r.result().path().getParent())
                    .distinct()
                    .count() == 1;
            if (sameDir) {
                return Optional.of(new Widget("open-folder", "Copy Folder Path", "action"));
            }
            return Optional.empty();
        }
    }

    /**
     * Activates the "Content Search Active" marker when the raw query contains
     * the {@code content:} qualifier.
     */
    public static final class ContentQualifierRule implements WidgetActivationRule {

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            if (query != null && query.contains("content:")) {
                return Optional.of(new Widget("search-content", "Content Search Active", "marker"));
            }
            return Optional.empty();
        }
    }

    /**
     * Always activates the "Export File List" action as long as the result set
     * is non-empty. This rule should be the last one registered so the export
     * widget always appears at the end of the widget strip.
     */
    public static final class AlwaysPresentRule implements WidgetActivationRule {

        @Override
        public Optional<Widget> evaluate(String query, List<RankedSearchResult> results) {
            return Optional.of(new Widget("export-list", "Export File List", "action"));
        }
    }
}
