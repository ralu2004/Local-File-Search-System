package app.repository;

/**
 * Compatibility aggregate for file persistence contracts.
 */
public interface FileRepository extends FileWriteRepository, FileSearchRepository, FileMetadataRepository {
}
