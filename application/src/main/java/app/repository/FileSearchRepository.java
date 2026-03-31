package app.repository;

import app.model.SearchResult;
import app.search.query.Query;

import java.sql.SQLException;
import java.util.List;

/**
 * Read-only search contract over indexed file data.
 */
public interface FileSearchRepository {
    List<SearchResult> search(Query query, int limit) throws SQLException;
}
