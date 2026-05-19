package app.search.query.preprocessor;

import app.search.query.Query;

import java.util.List;
import java.util.Map;

/**
 * Expands shorthand terms in the free-text part of the query using a configurable synonym map.
 * For example, "img" may expand to "img OR image OR photo".
 * Filter values (e.g. ext:img) are left untouched since they live in query.filters().
 */
public class SynonymDecorator implements QueryDecorator {

    private final QueryDecorator next;
    private final Map<String, List<String>> synonyms;

    /**
     * @param next     the next decorator in the chain
     * @param synonyms map of term to its expansions
     */
    public SynonymDecorator(QueryDecorator next, Map<String, List<String>> synonyms) {
        this.next = next;
        this.synonyms = synonyms;
    }

    @Override
    public Query decorate(Query query) {
        if (query.value() == null || query.value().isBlank()) {
            return next.decorate(query);
        }
        String[] tokens = query.value().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            if (synonyms.containsKey(token.toLowerCase())) {
                List<String> expanded = synonyms.get(token.toLowerCase());
                result.append("(").append(String.join(" OR ", expanded)).append(")");
            } else {
                result.append(token);
            }
        }
        return next.decorate(new Query(query.type(), result.toString(), query.filters()));
    }
}