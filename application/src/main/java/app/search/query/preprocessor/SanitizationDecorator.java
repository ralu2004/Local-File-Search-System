package app.search.query.preprocessor;

import app.search.query.Query;

/**
 * Strips characters from the free-text term that could break FTS5 query syntax.
 */
public class SanitizationDecorator implements QueryDecorator {

    private final QueryDecorator next;

    /**
     * @param next the next decorator in the chain
     */
    public SanitizationDecorator(QueryDecorator next) {
        this.next = next;
    }

    @Override
    public Query decorate(Query query) {
        if (query.value() == null || query.value().isBlank()) {
            return next.decorate(query);
        }
        String sanitized = query.value()
                .replaceAll("[';\\\\]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return next.decorate(new Query(query.type(), sanitized, query.filters()));
    }
}