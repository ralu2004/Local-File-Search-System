package app.model;

import java.util.List;

/**
 * Search result payload with optional ranking insights.
 */
public record RankedSearchResult(
        SearchResult result,
        List<String> insights
) {
}
