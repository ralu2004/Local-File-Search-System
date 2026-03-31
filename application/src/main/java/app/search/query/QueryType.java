package app.search.query;

/**
 * Classification of the parsed query input.
 */
public enum QueryType {
    FULLTEXT,
    FILENAME,
    METADATA,
    MIXED
}