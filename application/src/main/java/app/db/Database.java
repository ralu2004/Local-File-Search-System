package app.db;

import app.model.*;
import app.repository.FileRepository;
import app.search.query.Query;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Database implements FileRepository, AutoCloseable {

    private final Connection connection;
    private final QueryBuilder queryBuilder;

    public Database(String dbPath, QueryBuilder queryBuilder) throws SQLException, IOException {
        Path path = Paths.get(dbPath);
        Files.createDirectories(path.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(true);
        this.queryBuilder = queryBuilder;
        initializeSchema();
    }

    public Database(String dbPath) throws SQLException, IOException {
        this(dbPath, new QueryBuilder());
    }

    public Database() throws SQLException, IOException {
        this(System.getProperty("user.dir") + "/.searchengine/index.db");
    }

    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS files (
                        path        TEXT PRIMARY KEY,
                        filename    TEXT NOT NULL,
                        extension   TEXT,
                        size_bytes  INTEGER,
                        created_at  TEXT,
                        modified_at TEXT,
                        indexed_at  TEXT
                    );
                    """);
            stmt.execute("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS files_fts USING fts5(
                        path        UNINDEXED,
                        filename,
                        content,
                        preview     UNINDEXED
                    );
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS index_runs (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at  TEXT,
                        finished_at TEXT,
                        total_files INTEGER,
                        indexed     INTEGER,
                        skipped     INTEGER,
                        failed      INTEGER
                    );
                    """);
        }
    }

    @Override
    public void upsert(FileRecord record, String content, String preview) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String statement = """
                    INSERT OR REPLACE INTO files (path, filename, extension, size_bytes, created_at, modified_at, indexed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
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
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, record.path().toString());
                stmt.executeUpdate();
            }

            statement = """
                    INSERT OR REPLACE INTO files_fts (path, filename, content, preview)
                    VALUES (?, ?, ?, ?)
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, record.path().toString());
                stmt.setString(2, record.filename());
                stmt.setString(3, content);
                stmt.setString(4, preview);
                stmt.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public void delete(Path path) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String statement = "DELETE FROM files WHERE path = ?";
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, path.toString());
                stmt.executeUpdate();
            }

            statement = "DELETE FROM files_fts WHERE path = ?";
            try (PreparedStatement stmt = connection.prepareStatement(statement)) {
                stmt.setString(1, path.toString());
                stmt.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public int batchDelete(Set<Path> paths) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TEMP TABLE IF NOT EXISTS crawled_paths (path TEXT PRIMARY KEY);");

            String insert = "INSERT OR IGNORE INTO crawled_paths (path) VALUES (?)";
            try (PreparedStatement insertStmt = connection.prepareStatement(insert)) {
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

            connection.commit();
            return deleted;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public List<SearchResult> search(Query query) throws SQLException {
        BuiltQuery builtQuery = queryBuilder.build(query);
        return queryResults(builtQuery.sql(), builtQuery.params());
    }

    @Override
    public LocalDateTime getModifiedAt(Path path) throws SQLException {
        List<FileRecord> results = queryFiles("SELECT * FROM files WHERE path = ?", path.toString());
        return results.isEmpty() ? null : results.getFirst().modifiedAt();
    }

    @Override
    public FileRecord getByPath(Path path) throws SQLException {
        List<FileRecord> results = queryFiles("SELECT * FROM files WHERE path = ?", path.toString());
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<FileRecord> getAll() throws SQLException {
        return queryFiles("SELECT * FROM files");
    }

    @Override
    public List<FileRecord> getByExtension(String extension) throws SQLException {
        return queryFiles("SELECT * FROM files WHERE extension = ?", extension);
    }

    private List<FileRecord> queryFiles(String sql, Object... params) throws SQLException {
        List<FileRecord> files = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    files.add(mapFileRecord(rs));
                }
            }
        }
        return files;
    }

    private List<SearchResult> queryResults(String sql, List<Object> params) throws SQLException {
        List<SearchResult> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResult(rs));
                }
            }
        }
        return results;
    }

    private FileRecord mapFileRecord(ResultSet rs) throws SQLException {
        return new FileRecord(
                Path.of(rs.getString("path")),
                rs.getString("filename"),
                rs.getString("extension"),
                rs.getLong("size_bytes"),
                LocalDateTime.parse(rs.getString("created_at")),
                LocalDateTime.parse(rs.getString("modified_at"))
        );
    }

    private SearchResult mapResult(ResultSet rs) throws SQLException {
        return new SearchResult(
                Path.of(rs.getString("path")),
                rs.getString("filename"),
                rs.getString("extension"),
                rs.getString("preview"),
                LocalDateTime.parse(rs.getString("modified_at"))
        );
    }

    @Override
    public void close() throws Exception {
        if (!connection.isClosed()) {
            connection.close();
        }
    }
}