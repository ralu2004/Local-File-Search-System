package app.search.query.preprocessor;

import app.search.query.Query;

/**
 * Pre-processes a raw query string before it is parsed by {@link app.search.query.QueryParser}.
 * Implementations are designed as decorators, each wrapping the next in a chain.
 */
public interface QueryDecorator {

    Query decorate(Query query);
}
