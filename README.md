# Local File Search System

A local file search engine that indexes files on your machine and enables fast full-text and metadata search, with a CLI, an HTTP API, and a React-based web UI.

---

## Build

```bash
cd application
mvn package
```

The built jar will be at `application/target/application-1.0-SNAPSHOT.jar`.

---

## Usage

### Index a directory

```bash
java -jar application/target/application-1.0-SNAPSHOT.jar index <directory>
```

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `--db <path>` | `.searchengine/index.db` | Custom database path |
| `-i, --ignore <pattern>` | — | Glob pattern to ignore (repeatable) |
| `--max-file-size <MB>` | `10` | Skip files larger than this |
| `--preview-lines <n>` | `3` | Number of preview lines to store |
| `--batch-size <n>` | `250` | Number of files per DB batch write |

**Examples:**

```bash
# Basic index
java -jar ... index C:\Users\user\Documents

# With ignore rules
java -jar ... index C:\Users\user\Documents -i "*.log" -i "backup"

# Tune DB batch writes
java -jar ... index C:\Users\user\Documents --batch-size 500

# Custom database path
java -jar ... --db C:\myindex\index.db index C:\Users\user\Documents
```

### Search

```bash
java -jar application/target/application-1.0-SNAPSHOT.jar search "<query>"
```

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `--db <path>` | `.searchengine/index.db` | Custom database path |
| `--limit <n>` | `50` | Maximum number of results |

**Query syntax:**

| Query | Meaning |
|-------|---------|
| `getting started` | Full-text search |
| `README.md` | Search by filename |
| `content:hello` | Restrict full-text match to file contents |
| `path:src/main` | Filter by path substring (cross-platform) |
| `ext:java` | Filter by extension |
| `modified:2025-01-01` | Files modified after date |
| `size:1048576` | Files larger than size in bytes |
| `size:10kb`, `size:5mb`, `size:1gb` | Size filter with units (case-insensitive) |
| `sort:date`, `sort:alpha`, `sort:balanced`, `sort:behavior` | Choose ranking strategy |
| `config ext:json` | Combined full-text and metadata |

Qualifiers can appear in any order and combine with `AND` semantics. Duplicate qualifiers (e.g., two `content:` filters) compose with `AND`.

**Examples:**

```bash
java -jar ... search "getting started"
java -jar ... search "ext:java"
java -jar ... search "README.md"
java -jar ... search "size:10mb"
java -jar ... search "config ext:json" --limit 10
java -jar ... search "auth path:src/main sort:date"
```

---

## Web UI

The project includes a React frontend that talks to an HTTP API server.

**Start the API server:**

```bash
java -jar application/target/application-1.0-SNAPSHOT.jar server
```

The server listens on `http://localhost:7070` by default. To use a different port, pass it as the next argument: `... server 8080`.

**Run the frontend:**

```bash
cd frontend
npm install
npm run dev
```

The dev server prints the URL it's listening on. The frontend sends requests to `http://localhost:7070/api/*`.

The UI exposes both indexing and search workflows: configure a root directory and ignore rules, run indexing with live progress, then search with sort-mode selection (default, balanced, date, alphabetical, personalized). Results show file metadata, content previews with query-term highlighting, and a "Mark as opened" action that feeds personalized ranking. When the personalized sort is active, results display ranking insights describing why each result scored where it did.

---

## Default ignore rules

The crawler always ignores common system/build directories (for example `node_modules`, `target`, `build`, `dist`, `.git`, `.idea`, `AppData`, `Program Files`, `Windows`) and also ignores hidden files/directories and non-text files. You can add more rules with `-i/--ignore`.

---

## Incremental indexing verification

To validate that only changed files are re-indexed, do the following steps:

1. Run an initial index on a test directory.
2. Modify one indexed text file, add one new file, and delete one existing indexed file.
3. Run indexing again on the same directory.
4. Check the report:
   - `Skipped` should include unchanged files.
   - `Indexed` should reflect only changed/new files.
   - `Deleted` should include files removed from disk.

---

## Testing

Run all automated tests:

```bash
cd application
mvn test
```

Current suite covers:

- **Query parsing**: full-text, filename, metadata, mixed input, and `size` unit parsing (`bytes`, `kb`, `mb`, `gb`)
- **Search behavior**: recursive traversal, single-word and multi-word full-text search
- **Metadata filters**: `ext`, `modified`, `size` (including unit forms), `path`, `content`
- **Runtime indexing options**: `ignoreRules`, `maxFileSizeMb`, `previewLines`, `batchSize`
- **Indexing lifecycle**: background progress snapshot and final report
- **Resilience**: database failure propagation, unreadable files, and symlink-loop environments (platform-dependent skip)
- **Incremental indexing**: unchanged-file skip, modified-file update, and deleted-file cleanup
- **Ranking strategies**: resolver mapping, swappable strategy selection, behavior score formula (frequency, recency, position lift), ranking insight formatting (relative time, lift threshold)
- **Search activity**: history recording, suggestion prefix matching, recent-query ordering

Typical output should report all tests passing, with one optional skipped test on platforms that cannot create symlinks.

---

## Personalized ranking

The default ranking favors content relevance and path features. The personalized ranking strategy (`sort:behavior`, or "Personalized" in the UI) reorders results based on the user's interaction history with similar queries. It uses three signals:

- **Frequency** — how often the file has been opened for the same normalized query
- **Recency** — how recently it was opened (exponential decay with a 7-day half-life)
- **Position lift/boost** — whether the user typically had to "dig" past higher-ranked results to reach this file (an opened file consistently found at position 8 ranks higher than one always found at position 1, holding other factors equal)

Files split into two buckets: those with any open history sort first by behavior score, those without sort after by full-text relevance. When the personalized sort is active, the UI shows insights under each result explaining the ranking ("you've opened this 5 times for similar searches", "last opened 2 hours ago", "you often find this past higher-ranked results").

The UI also surfaces query suggestions based on prefix matches against search history, and recent unique queries — both fed by the same activity tracking that drives personalized ranking.

---

## Design notes

The following sections document the architectural choices of the ranking system, including the trade-offs that were considered and deliberately accepted.

### Behavior score: SQLite UDF instead of inline SQL

The personalized ranking formula combines three signals into a single weighted score. Two implementation paths were considered:

**Option A — formula inline in the strategy's `ORDER BY` clause.** Fits the existing `RankingStrategy` contract (each strategy returns a SQL fragment). Simple to wire in, but the formula lives as a string concatenation in Java code and cannot be unit-tested without a live SQLite connection.

**Option B — formula in a Java class, exposed to SQL through a SQLite user-defined function.** Requires registering the UDF on every JDBC connection (introducing a connection-level coupling), but keeps the formula in a unit-testable Java class while preserving the `RankingStrategy` contract.

The current implementation features option B. The formula lives in `BehaviorScoreFormula` with `BehaviorScoreFormulaTest` covering each component (frequency cap, recency half-life, position lift threshold) as pure-function tests. The thin SQLite adapter (`SqliteBehaviorScoreFunction`) extends `org.sqlite.Function` and delegates to the formula. The strategy class (`BehaviorRankingStrategy`) returns an `ORDER BY` clause referencing the UDF by its registered name.

### Explainability: per-strategy opt-in, not per-result tagging

When the user picks personalized sort, results show insights describing why they ranked. The insight text is generated from the same raw signals that feed the formula (open count, last-open timestamp, average position) and lives in `BehaviorRankingInsights`. Backend production of insights is gated by `RankingStrategy.producesInsights()`, a default method that returns `false` and is overridden to `true` only on `BehaviorRankingStrategy`. Other strategies' result rows carry an empty insight list.

### Persistence layer: dependency inversion before decomposition

The persistence layer underwent two refactors during Iteration 2:

**1. Narrow consumer dependencies.** Services that previously held references to a god `Database` class were updated to depend on narrow repository interfaces (`FileSearchRepository`, `SearchActivityRepository`, `IndexRunRepository`, `FileWriteRepository`, `FileMetadataRepository`). Each interface has a `Closeable*` companion (e.g., `CloseableFileSearch`) extending `AutoCloseable`, so services can use `try-with-resources` while still depending on a narrow type. The `DatabaseAccessor` is the only place that constructs persistence handles; services never see the umbrella type.

**2. Decompose the implementation.** With consumers decoupled, the monolithic `Database` class was split into three per-domain context classes:
- `FileContext` — file records (search, write, metadata)
- `IndexRunContext` — indexing run lifecycle and history
- `ActivityContext` — search execution and result-open activity

`SqliteDatabaseSession` composes the three contexts and implements the umbrella `DatabaseSession` interface (which aggregates the closeable views). `SqliteDatabaseProvider` returns `DatabaseSession` instances and now owns schema initialization (extracted from the previous `Database` constructor). The `Database` class no longer exists.

### Frontend structure

`App.tsx` is the top-level component owning section state and layout. Indexing concerns (config form, status badge, history table) live in `IndexPanel`; search concerns (search bar, sort selector, results, insights) live in `SearchPanel`. Leaf components (`SuggestionBox`, `ResultCard`, `StatusBadge`) are presentational. All HTTP calls are centralized in `api/client.ts`. Shared types live in `types.ts`. Pure utilities (`formatFileSize`, `getFolderPath`, `highlightText`) are extracted to the `utils/*` modules.

---

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the C4 model of the system design (delivered as part of an earlier iteration). The "Design notes" section above documents iteration-2 additions and refactors not covered by the original architecture document.