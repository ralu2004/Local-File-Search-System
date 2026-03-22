package app.db;

import app.indexer.IndexReport;
import app.model.*;
import app.repository.FileRepository;
import app.repository.IndexRunRepository;
import app.search.query.Query;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Database implements FileRepository, IndexRunRepository, AutoCloseable {

    private final Connection connection;
    private final QueryBuilder queryBuilder;

    public Database(String dbPath, QueryBuilder queryBuilder) throws SQLException, IOException {
        Path path = Paths.get(dbPath);
        Files.createDirectories(path.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        connection.setAutoCommit(true);
        this.queryBuilder = queryBuilder;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
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
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_extension ON files(extension);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_files_modified_at ON files(modified_at);");
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
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at      TEXT,
                        finished_at     TEXT,
                        total_files     INTEGER,
                        indexed         INTEGER,
                        skipped         INTEGER,
                        failed          INTEGER,
                        deleted         INTEGER,
                        elapsed_seconds INTEGER
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
    public List<SearchResult> search(Query query, int limit) throws SQLException {
        BuiltQuery builtQuery = queryBuilder.build(query, limit);
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

    @Override
    public long startIndexing(LocalDateTime startedAt) throws SQLException {
        String statement = "INSERT INTO index_runs (started_at) VALUES (?)";
        try (PreparedStatement stmt = connection.prepareStatement(statement, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, startedAt.toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        }
        return 0;
    }

    @Override
    public void endIndexing(long runId, IndexReport report) throws SQLException {
        String statement = """
                UPDATE index_runs
                SET finished_at=?, total_files=?, indexed=?, skipped=?, failed=?, deleted=?, elapsed_seconds=?
                WHERE id=?
                """;
        connection.setAutoCommit(false);
        try (PreparedStatement stmt = connection.prepareStatement(statement)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setInt(2, report.totalFiles());
            stmt.setInt(3, report.indexed());
            stmt.setInt(4, report.skipped());
            stmt.setInt(5, report.failed());
            stmt.setInt(6, report.deleted());
            stmt.setLong(7, report.elapsed().toSeconds());
            stmt.setLong(8, runId);
            stmt.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public List<IndexRun> getHistory() throws SQLException {
        return queryIndexRuns("SELECT * FROM index_runs ORDER BY started_at DESC");
    }

    @Override
    public void optimizeFts() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO files_fts(files_fts) VALUES('optimize');");
        }
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

    private List<IndexRun> queryIndexRuns(String sql, Object... params) throws SQLException {
        List<IndexRun> runs = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(mapIndexRun(rs));
                }
            }
        }
        return runs;
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

    private IndexRun mapIndexRun(ResultSet rs) throws SQLException {
        LocalDateTime startedAt = LocalDateTime.parse(rs.getString("started_at"));
        String finishedAtStr = rs.getString("finished_at");
        LocalDateTime finishedAt = finishedAtStr != null ? LocalDateTime.parse(finishedAtStr) : null;
        Duration elapsed = Duration.ofSeconds(rs.getLong("elapsed_seconds"));

        return new IndexRun(
                rs.getLong("id"),
                startedAt,
                finishedAt,
                rs.getInt("total_files"),
                rs.getInt("indexed"),
                rs.getInt("skipped"),
                rs.getInt("failed"),
                rs.getInt("deleted"),
                elapsed
        );
    }

    @Override
    public void close() throws Exception {
        if (!connection.isClosed()) {
            connection.close();
        }
    }
}