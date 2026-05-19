package app.search.query.preprocessor;

import app.search.query.Query;

/**
 * Terminal decorator that returns the query unchanged.
 * Used as the base of the decorator chain.
 */
public class IdentityDecorator implements QueryDecorator {

    @Override
    public Query decorate(Query query) {
        return query;
    }
}