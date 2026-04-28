package app.server;

import app.db.DatabaseProvider;
import app.db.SqliteDatabaseProvider;
import app.indexer.job.BackgroundIndexer;
import app.indexer.job.IndexingJobSnapshot;
import app.model.IndexRun;
import app.service.index.HistoryService;
import app.service.index.IndexService;
import app.service.search.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.List;

/**
 * HTTP API server for indexing and searching indexed files.
 * <p>
 * Exposes endpoints for health, index start/status/history, and search.
 */
public class ApiServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    private static final int DEFAULT_PORT = 7070;

    private final int port;
    private final BackgroundIndexer backgroundIndexer;
    private final IndexService indexService;
    private final HistoryService historyService;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;
    private Javalin app;

    public ApiServer() {
        this(DEFAULT_PORT, new SqliteDatabaseProvider());
    }

    public ApiServer(int port) {
        this(port, new SqliteDatabaseProvider());
    }

    public ApiServer(int port, DatabaseProvider databaseProvider) {
        this.port = port;
        this.backgroundIndexer = new BackgroundIndexer();
        this.indexService = new IndexService(databaseProvider);
        this.historyService = new HistoryService(databaseProvider);
        this.searchService = new SearchService(databaseProvider);
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public void start() {
        app = Javalin.create(config -> config.plugins.enableCors(cors ->
                cors.add(rule -> rule.anyHost())
        ));

        app.exception(IllegalArgumentException.class, (e, ctx) -> {
            log.warn("Bad request: {}", e.getMessage());
            writeJson(ctx.status(400), new ErrorResponse("BAD_REQUEST", e.getMessage()));
        });
        app.exception(SQLException.class, (e, ctx) -> {
            log.error("Database error while handling request", e);
            writeJson(ctx.status(500), new ErrorResponse("DB_ERROR", "Database error."));
        });
        app.exception(IOException.class, (e, ctx) -> {
            log.error("I/O error while handling request", e);
            writeJson(ctx.status(500), new ErrorResponse("IO_ERROR", "I/O error."));
        });
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled server error", e);
            writeJson(ctx.status(500), new ErrorResponse("SERVER_ERROR", "Server error."));
        });

        app.get("/api/health", ctx -> writeJson(ctx, new HealthResponse("ok")));

        app.post("/api/index/start", ctx -> {
            IndexStartRequest request;
            try {
                request = ctx.bodyAsClass(IndexStartRequest.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body.");
            }
            validateStartRequest(request);
            boolean started = backgroundIndexer.start(live ->
                    indexService.createBackgroundIndexer(
                            request.dbPath(),
                            request.root(),
                            request.ignoreRules(),
                            request.maxFileSizeMb(),
                            request.previewLines(),
                            request.batchSize(),
                            live
                    )
            );
            if (!started) {
                writeJson(ctx.status(409), new ErrorResponse("JOB_ALREADY_RUNNING", "Indexing job is already running."));
                return;
            }
            writeJson(ctx.status(202), new MessageResponse("Indexing job started."));
        });

        app.get("/api/index/status", ctx -> {
            IndexingJobSnapshot snapshot = backgroundIndexer.getSnapshot();
            writeJson(ctx, snapshot);
        });

        app.get("/api/index/history", ctx -> {
            String dbPath = ctx.queryParam("db");
            int limit = parsePositiveInt(ctx.queryParam("limit"), 20);
            int capped = Math.min(Math.max(limit, 1), 100);
            try {
                List<IndexRun> runs = historyService.getHistory(dbPath, capped);
                List<IndexRunResponse> body = runs.stream().map(IndexRunResponse::from).toList();
                writeJson(ctx, body);
            } catch (SQLException | IOException e) {
                log.error("Failed to load index history", e);
                writeJson(ctx.status(500), new ErrorResponse("INDEX_HISTORY_FAILED", "Failed to load index history."));
            }
        });

        app.get("/api/search", ctx -> {
            String query = ctx.queryParam("q");
            if (query == null) query = "";
            String dbPath = ctx.queryParam("db");
            int limit = parsePositiveInt(ctx.queryParam("limit"), 50);
            try {
                writeJson(ctx, searchService.search(dbPath, query, limit));
            } catch (SQLException | IOException e) {
                log.error("Search failed", e);
                writeJson(ctx.status(500), new ErrorResponse("SEARCH_FAILED", "Search failed."));
            }
        });

        app.get("/api/search/suggest", ctx -> {
            String query = ctx.queryParam("q");
            if (query == null) query = "";
            String dbPath = ctx.queryParam("db");
            int limit = parsePositiveInt(ctx.queryParam("limit"), 10);
            int capped = Math.min(Math.max(limit, 1), 20);
            try {
                writeJson(ctx, searchService.suggestQueries(dbPath, query, capped));
            } catch (SQLException | IOException e) {
                log.error("Search suggest failed", e);
                writeJson(ctx.status(500), new ErrorResponse("SEARCH_SUGGEST_FAILED", "Search suggest failed."));
            }
        });

        app.get("/api/search/history", ctx -> {
            String dbPath = ctx.queryParam("db");
            int limit = parsePositiveInt(ctx.queryParam("limit"), 10);
            int capped = Math.min(Math.max(limit, 1), 20);
            try {
                writeJson(ctx, searchService.recentQueries(dbPath, capped));
            } catch (SQLException | IOException e) {
                log.error("Search history failed", e);
                writeJson(ctx.status(500), new ErrorResponse("SEARCH_HISTORY_FAILED", "Search history failed."));
            }
        });

        app.post("/api/search/open", ctx -> {
            SearchOpenRequest request;
            try {
                request = ctx.bodyAsClass(SearchOpenRequest.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON body.");
            }
            validateSearchOpenRequest(request);
            try {
                searchService.recordResultOpen(
                        request.dbPath(),
                        request.query(),
                        request.filePath(),
                        request.resultPosition()
                );
                writeJson(ctx.status(202), new MessageResponse("Search open event recorded."));
            } catch (SQLException | IOException e) {
                log.error("Search open tracking failed", e);
                writeJson(ctx.status(500), new ErrorResponse("SEARCH_OPEN_TRACK_FAILED", "Search open tracking failed."));
            }
        });

        app.start(port);
        log.info("API server started on port {}", port);
    }

    private void validateStartRequest(IndexStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.root() == null || request.root().isBlank()) {
            throw new IllegalArgumentException("Field 'root' is required and must be a valid directory path.");
        }
    }

    private void validateSearchOpenRequest(SearchOpenRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.filePath() == null || request.filePath().isBlank()) {
            throw new IllegalArgumentException("Field 'filePath' is required.");
        }
    }

    private void writeJson(io.javalin.http.Context ctx, Object body) {
        try {
            ctx.contentType("application/json");
            ctx.result(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Failed to serialize JSON response", e));
        }
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void close() {
        if (app != null) {
            app.stop();
        }
        backgroundIndexer.close();
    }

    /**
     * Request payload for starting a new indexing job.
     */
    public record IndexStartRequest(
            String root,
            String dbPath,
            List<String> ignoreRules,
            int maxFileSizeMb,
            int previewLines,
            int batchSize
    ) {}

    /**
     * Generic success response payload.
     */
    public record MessageResponse(String message) {}

    /**
     * Request payload for recording that a result was opened by the user.
     */
    public record SearchOpenRequest(
            String dbPath,
            String query,
            String filePath,
            Integer resultPosition
    ) {}

    /**
     * Generic error response payload.
     */
    public record ErrorResponse(String code, String message) {}

    /**
     * Health-check response payload.
     */
    public record HealthResponse(String status) {}

    /**
     * API-facing representation of an indexing history entry.
     */
    public record IndexRunResponse(
            long id,
            String startedAt,
            String finishedAt,
            String rootPath,
            int totalFiles,
            int indexed,
            int skipped,
            int failed,
            int deleted,
            double elapsedSeconds
    ) {
        static IndexRunResponse from(IndexRun run) {
            var elapsed = run.elapsed();
            double elapsedSeconds = elapsed.getSeconds() + elapsed.getNano() / 1_000_000_000.0;
            return new IndexRunResponse(
                    run.id(),
                    run.startedAt() != null ? run.startedAt().toString() : null,
                    run.finishedAt() != null ? run.finishedAt().toString() : null,
                    run.rootPath(),
                    run.totalFiles(),
                    run.indexed(),
                    run.skipped(),
                    run.failed(),
                    run.deleted(),
                    elapsedSeconds
            );
        }
    }
}
