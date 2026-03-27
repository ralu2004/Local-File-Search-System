package app.server;

import app.crawler.Crawler;
import app.db.Database;
import app.extractor.Extractor;
import app.indexer.Indexer;
import app.indexer.job.BackgroundIndexer;
import app.indexer.job.IndexingJobSnapshot;
import app.indexer.job.IndexingLiveProgress;
import app.model.IndexRun;
import app.search.SearchEngine;
import app.search.query.QueryParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.List;

public class ApiServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApiServer.class);
    private static final int DEFAULT_PORT = 7070;

    private final int port;
    private final BackgroundIndexer backgroundIndexer;
    private final ObjectMapper objectMapper;
    private Javalin app;

    public ApiServer() {
        this(DEFAULT_PORT);
    }

    public ApiServer(int port) {
        this.port = port;
        this.backgroundIndexer = new BackgroundIndexer();
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
            boolean started = backgroundIndexer.start(live -> buildIndexer(request, live));
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
            try (Database db = dbPath != null && !dbPath.isBlank() ? new Database(dbPath) : new Database()) {
                List<IndexRun> runs = db.getHistory();
                List<IndexRunResponse> body = runs.stream()
                        .limit(capped)
                        .map(IndexRunResponse::from)
                        .toList();
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
            try (Database db = dbPath != null && !dbPath.isBlank() ? new Database(dbPath) : new Database()) {
                SearchEngine engine = new SearchEngine(db, new QueryParser(), limit);
                writeJson(ctx, engine.search(query));
            } catch (SQLException | IOException e) {
                log.error("Search failed", e);
                writeJson(ctx.status(500), new ErrorResponse("SEARCH_FAILED", "Search failed."));
            }
        });

        app.start(port);
        log.info("API server started on port {}", port);
    }

    private Indexer buildIndexer(IndexStartRequest request, IndexingLiveProgress liveProgress) {
        String dbPath = request.dbPath() == null || request.dbPath().isBlank() ? null : request.dbPath();
        int maxFileSizeMb = request.maxFileSizeMb() <= 0 ? 10 : request.maxFileSizeMb();
        int previewLines = request.previewLines() <= 0 ? 3 : request.previewLines();
        int batchSize = request.batchSize() <= 0 ? 250 : request.batchSize();
        List<String> ignoreRules = request.ignoreRules() == null ? List.of() : request.ignoreRules();

        try {
            Database db = dbPath != null ? new Database(dbPath) : new Database();
            Crawler crawler = new Crawler(java.nio.file.Path.of(request.root()), ignoreRules);
            Extractor extractor = new Extractor(previewLines, (long) maxFileSizeMb * 1024 * 1024);
            return new Indexer(db, db, crawler, extractor, batchSize, liveProgress) {
                @Override
                public app.indexer.IndexReport run() {
                    try (db) {
                        return super.run();
                    }
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("Cannot start indexer: " + e.getMessage(), e);
        }
    }

    private void validateStartRequest(IndexStartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.root() == null || request.root().isBlank()) {
            throw new IllegalArgumentException("Field 'root' is required and must be a valid directory path.");
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

    public record IndexStartRequest(
            String root,
            String dbPath,
            List<String> ignoreRules,
            int maxFileSizeMb,
            int previewLines,
            int batchSize
    ) {}

    public record MessageResponse(String message) {}

    public record ErrorResponse(String code, String message) {}

    public record HealthResponse(String status) {}

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
