package app.db.sqlite;

import app.db.BuiltQuery;
import app.db.QueryBuilder;
import app.model.ExtractedRecord;
import app.model.FileRecord;
import app.model.SearchResult;
import app.repository.FileRepository;
import app.search.query.Query;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public final class SqliteFileRepository implements FileRepository {

    private static final String FILE_COLUMNS = "path, filename, extension, size_bytes, created_at, modified_at";

    private final QueryBuilder queryBuilder;
    private final SqliteConnectionProvider connections;

    public SqliteFileRepository(SqliteConnectionProvider connections, QueryBuilder queryBuilder) {
        this.connections = connections;
        this.queryBuilder = queryBuilder;
    }

    @Override
    public void upsert(FileRecord record, String content, String preview) throws SQLException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                String statement = """
                        INSERT OR REPLACE INTO files (path, filename, extension, size_bytes, created_at, modified_at, indexed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(statement)) {
                    stmt.setString(1, record.path().toString());
                    stmt.setString(2, record.filename());
                    stmt.setString(3, record.extension());
                    stmt.setLong(4, record.sizeBytes());
                    stmt.setString(5, record.createdAt().toString());
                    stmt.setString(6, record.modifiedAt().toString());
                    stmt.setString(7, LocalDateTime.now().toString());
                    stmt.executeUpdate();
                }

                statement = "DELETE FROM files_fts WHERE path = ?";
                try (PreparedStatement stmt = conn.prepareStatement(statement)) {
                    stmt.setString(1, record.path().toString());
                    stmt.executeUpdate();
                }

                statement = """
                        INSERT OR REPLACE INTO files_fts (path, filename, content, preview)
                        VALUES (?, ?, ?, ?)
                        """;
                try (PreparedStatement stmt = conn.prepareStatement(statement)) {
                    stmt.setString(1, record.path().toString());
                    stmt.setString(2, record.filename());
                    stmt.setString(3, content);
                    stmt.setString(4, preview);
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void batchUpsert(List<ExtractedRecord> records) throws SQLException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                String filesStmt = """
                        INSERT OR REPLACE INTO files (path, filename, extension, size_bytes, created_at, modified_at, indexed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;
                String deleteFts = "DELETE FROM files_fts WHERE path = ?";
                String ftsStmt = """
                        INSERT INTO files_fts (path, filename, content, preview)
                        VALUES (?, ?, ?, ?)
                        """;

                try (PreparedStatement fs = conn.prepareStatement(filesStmt);
                     PreparedStatement df = conn.prepareStatement(deleteFts);
                     PreparedStatement fts = conn.prepareStatement(ftsStmt)) {

                    for (ExtractedRecord r : records) {
                        fs.setString(1, r.record().path().toString());
                        fs.setString(2, r.record().filename());
                        fs.setString(3, r.record().extension());
                        fs.setLong(4, r.record().sizeBytes());
                        fs.setString(5, r.record().createdAt().toString());
                        fs.setString(6, r.record().modifiedAt().toString());
                        fs.setString(7, LocalDateTime.now().toString());
                        fs.addBatch();

                        df.setString(1, r.record().path().toString());
                        df.addBatch();

                        fts.setString(1, r.record().path().toString());
                        fts.setString(2, r.record().filename());
                        fts.setString(3, r.content());
                        fts.setString(4, r.preview());
                        fts.addBatch();
                    }

                    fs.executeBatch();
                    df.executeBatch();
                    fts.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void delete(Path path) throws SQLException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try {
                String statement = "DELETE FROM files WHERE path = ?";
                try (PreparedStatement stmt = conn.prepareStatement(statement)) {
                    stmt.setString(1, path.toString());
                    stmt.executeUpdate();
                }

                statement = "DELETE FROM files_fts WHERE path = ?";
                try (PreparedStatement stmt = conn.prepareStatement(statement)) {
                    stmt.setString(1, path.toString());
                    stmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public int batchDelete(Set<Path> paths) throws SQLException {
        try (Connection conn = connections.open()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TEMP TABLE IF NOT EXISTS crawled_paths (path TEXT PRIMARY KEY);");

                String insert = "INSERT OR IGNORE INTO crawled_paths (path) VALUES (?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insert)) {
                    for (Path path : paths) {
                        insertStmt.setString(1, path.toString());
                        insertStmt.addBatch();
                    }
                    insertStmt.executeBatch();
                }

                int deleted = 0;
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM files WHERE path NOT IN (SELECT path FROM crawled_paths);")) {
                    if (rs.next()) deleted = rs.getInt(1);
                }

                stmt.execute("DELETE FROM files WHERE path NOT IN (SELECT path FROM crawled_paths);");
                stmt.execute("DELETE FROM files_fts WHERE path NOT IN (SELECT path FROM crawled_paths);");
                stmt.execute("DROP TABLE IF EXISTS crawled_paths;");

                conn.commit();
                return deleted;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<SearchResult> search(Query query, int limit) throws SQLException {
        BuiltQuery builtQuery = queryBuilder.build(query, limit);
        return queryResults(builtQuery.sql(), builtQuery.params());
    }

    @Override
    public LocalDateTime getModifiedAt(Path path) throws SQLException {
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement("SELECT modified_at FROM files WHERE path = ?")) {
            stmt.setString(1, path.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                String modifiedAt = rs.getString("modified_at");
                return modifiedAt == null ? null : LocalDateTime.parse(modifiedAt);
            }
        }
    }

    @Override
    public Map<Path, LocalDateTime> getAllModifiedAtByPath() throws SQLException {
        Map<Path, LocalDateTime> modifiedAtByPath = new HashMap<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement("SELECT path, modified_at FROM files");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String modifiedAt = rs.getString("modified_at");
                if (modifiedAt == null) continue;
                modifiedAtByPath.put(Path.of(rs.getString("path")), LocalDateTime.parse(modifiedAt));
            }
        }
        return modifiedAtByPath;
    }

    @Override
    public FileRecord getByPath(Path path) throws SQLException {
        List<FileRecord> results = queryFiles("SELECT " + FILE_COLUMNS + " FROM files WHERE path = ?", path.toString());
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<FileRecord> getAll() throws SQLException {
        return queryFiles("SELECT " + FILE_COLUMNS + " FROM files");
    }

    @Override
    public List<FileRecord> getByExtension(String extension) throws SQLException {
        return queryFiles("SELECT " + FILE_COLUMNS + " FROM files WHERE extension = ?", extension);
    }

    @Override
    public void optimizeFts() throws SQLException {
        try (Connection conn = connections.open();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO files_fts(files_fts) VALUES('optimize');");
        }
    }

    private List<FileRecord> queryFiles(String sql, Object... params) throws SQLException {
        List<FileRecord> files = new ArrayList<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(SqliteRowMappers.fileRecord(rs));
                }
            }
        }
        return files;
    }

    private List<SearchResult> queryResults(String sql, List<Object> params) throws SQLException {
        List<SearchResult> results = new ArrayList<>();
        try (Connection conn = connections.open();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(SqliteRowMappers.searchResult(rs));
                }
            }
        }
        return results;
    }
}

