package app.db;

import app.repository.CloseableFileMetadata;
import app.repository.CloseableFileSearch;
import app.repository.CloseableFileWrite;
import app.repository.CloseableIndexRuns;
import app.repository.CloseableIndexSession;
import app.repository.CloseableSearchActivity;

/**
 * Aggregates per-domain closeable repository views.
 * Returned by {@link DatabaseProvider#open}; consumed by
 * {@link app.service.support.DatabaseAccessor} which narrows to specific
 * closeable views per service operation.
 *
 * <p>This interface is the boundary type of the persistence subsystem; services 
 * never depend on it directly; they receive narrow closeable views via the accessor.
 */
public interface DatabaseSession extends
        CloseableFileSearch, CloseableSearchActivity, CloseableIndexRuns,
        CloseableFileWrite, CloseableFileMetadata, CloseableIndexSession {
}
