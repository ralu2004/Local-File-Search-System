package app.service.support;

/**
 * Shared query-text normalization used by search history and ranking signals.
 */
public final class QueryNormalizer {

    private static final String SORT_QUALIFIER_REGEX = "\\bsort:[^\\s]+";

    private QueryNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase();
    }

    /**
     * Normalizes a query for behavior/history keys, removing UI sort qualifiers.
     * <p>
     * Example: {@code "  docs sort:behavior  " -> "docs"}.
     */
    public static String normalizeForHistory(String input) {
        if (input == null) {
            return "";
        }
        String withoutSort = input.replaceAll(SORT_QUALIFIER_REGEX, "");
        return normalize(withoutSort.replaceAll("\\s+", " "));
    }
}
