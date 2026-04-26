package app.service.support;

/**
 * Shared query-text normalization used by search history and ranking signals.
 */
public final class QueryNormalizer {

    private QueryNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase();
    }
}
