package app.model;

import java.util.List;

/**
 * Search result payload with optional ranking insights.
 *
 * @param result base search hit data rendered by clients
 * @param insights user-facing ranking explanations; typically empty for
 *                 non-behavior strategies
 */
public record RankedSearchResult(
        SearchResult result,
        List<String> insights
) {
}
