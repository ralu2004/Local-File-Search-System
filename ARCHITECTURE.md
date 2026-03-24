# Local File Search System Architecture

This document presents the architecture of the Local File Search System using the C4 model.  

## Scope And Notation

- **System**: the Local File Search System as a whole
- **Container**: a separately executable unit or persistent data store
- **Component**: a major building block inside a container or shared runtime
- **Class**: selected code-level structures used to illustrate the design

Legend:
- boxes = software systems / containers / components
- cylinders = persistent data stores
- arrows = main communication or dependency direction

---

## 1. System Context (Level 1)

At the highest level, the system enables a user to index a local folder and search its contents and metadata efficiently on their own machine.

```mermaid
graph TD
    User["User"]
    System["Local File Search System"]
    FS["Local Filesystem"]

    User -->|indexes folders, runs searches, reviews results| System
    System -->|reads file metadata and content| FS
```

### Context Summary

| Entity | Role |
|---|---|
| `User` | Starts indexing, configures options, runs queries, inspects results |
| `Local File Search System` | Crawls files, extracts searchable content, stores indexes, serves searches |
| `Local Filesystem` | External source of files, paths, timestamps, and contents |

---

## 2. Containers (Level 2)

The system is composed of a small number of local containers. The most important boundary at this level is between user-facing clients and the Java backend services that execute indexing and search logic.

```mermaid
graph TD
    User["User"]
    FS["Local Filesystem"]

    subgraph System["Local File Search System"]
        GUI["Desktop GUI / Renderer
[Electron + React + TypeScript]"]
        API["Local API Server
[Java + Javalin]"]
        CLI["CLI Application
[Java + picocli]"]
        DB[("SQLite Database
[SQLite + FTS5]")]
    end

    User -->|uses| GUI
    User -->|uses| CLI
    GUI -->|HTTP/JSON on localhost| API
    API -->|reads/writes index| DB
    CLI -->|reads/writes index| DB
    API -->|reads files during indexing| FS
    CLI -->|reads files during indexing| FS
```

### Container Responsibilities

| Container | Technology | Responsibility |
|---|---|---|
| `Desktop GUI / Renderer` | Electron + React + TypeScript | Presents indexing and search screens, calls the local API, renders progress and results |
| `Local API Server` | Java + Javalin | Exposes endpoints for indexing, indexing status/history, and search |
| `CLI Application` | Java + picocli | Provides command-line indexing and search without the GUI |
| `SQLite Database` | SQLite with FTS5 | Stores file metadata, searchable text, previews, and index run history |

### Why this structure?

- The GUI can evolve independently from the indexing and search logic.
- The CLI and API server reuse the same domain logic instead of duplicating it.
- SQLite is an implementation detail hidden behind repository interfaces and query-building code.
- A future web frontend or different client could reuse the existing local API without changing the indexing internals.

---

## 3. Components (Level 3)

The following diagrams show the component structure from two complementary perspectives: orchestration and domain flow.

### 3.1 Backend Orchestration View

```mermaid
graph TD
    subgraph Backend["Java Backend Runtime"]
        Main["Main"]
        ApiServer["ApiServer"]
        Cli["CLI"]
        BgIndexer["BackgroundIndexer"]
        SearchEngine["SearchEngine"]
        Indexer["Indexer"]
        Database["Database"]
    end

    Main -->|starts server mode| ApiServer
    Main -->|starts CLI mode| Cli
    ApiServer -->|creates/runs| BgIndexer
    ApiServer -->|executes search| SearchEngine
    Cli -->|executes indexing| Indexer
    Cli -->|executes search| SearchEngine
    BgIndexer -->|runs asynchronously| Indexer
    SearchEngine -->|queries through repositories| Database
    Indexer -->|stores records and history| Database
```

### 3.2 Indexing And Search Flow View

```mermaid
graph TD
    subgraph SharedLogic["Shared Backend Components"]
        Crawler["Crawler"]
        Extractor["Extractor"]
        Indexer["Indexer"]
        Search["SearchEngine"]
        Parser["QueryParser"]
        DB["Database"]
        QB["QueryBuilder"]
    end

    Crawler -->|emits FileRecord| Indexer
    Indexer -->|extract content + preview| Extractor
    Indexer -->|persist metadata, preview, history| DB
    Search -->|parse user query| Parser
    Search -->|delegate query execution| DB
    DB -->|build SQL from Query| QB
```

### Component Responsibilities

| Component | Responsibility |
|---|---|
| `Main` | Chooses entry mode: local API server or CLI |
| `ApiServer` | Defines REST endpoints, validates request data, maps JSON to backend calls |
| `CLI` | Parses commands/options and invokes backend operations directly |
| `BackgroundIndexer` | Runs indexing asynchronously and exposes job snapshots for polling |
| `Crawler` | Walks the local directory tree and produces `FileRecord` metadata |
| `Extractor` | Reads file content and generates stored preview snippets |
| `Indexer` | Coordinates incremental indexing, batching, deletion handling, and run statistics |
| `SearchEngine` | Converts user input into a query and retrieves matching results |
| `QueryParser` | Interprets query syntax such as text terms and metadata filters |
| `Database` | Central persistence component; implements file and index-run repositories |
| `QueryBuilder` | Converts parsed queries into SQLite/FTS SQL |

---

## 4. Classes (Level 4)

The following class diagram focuses on the most important code-level relationships rather than attempting to show every class in the codebase.

```mermaid
classDiagram
    class FileRepository {
        <<interface>>
        +upsert(FileRecord, String, String)
        +batchUpsert(List~ExtractedRecord~)
        +search(Query, int) List~SearchResult~
        +getAllModifiedAtByPath() Map~Path, LocalDateTime~
    }

    class IndexRunRepository {
        <<interface>>
        +startIndexing(LocalDateTime, String) long
        +endIndexing(long, IndexReport)
        +getHistory() List~IndexRun~
    }

    class Database {
        -String jdbcUrl
        -QueryBuilder queryBuilder
        +Database()
        +Database(String)
    }

    class Crawler {
        -Path root
        -List~PathMatcher~ matchers
        +getRoot() Path
        +crawl(Consumer~FileRecord~)
    }

    class Extractor {
        -int previewLines
        -long maxFileSize
    }

    class Indexer {
        -int batchSize
        +run() IndexReport
    }

    class BackgroundIndexer {
        +start(Function~IndexingLiveProgress, Indexer~) boolean
        +getSnapshot() IndexingJobSnapshot
        +isRunning() boolean
    }

    class IndexingJobSnapshot {
        <<record>>
        +status IndexingJobStatus
        +startedAt LocalDateTime
        +finishedAt LocalDateTime
        +lastReport IndexReport
        +lastError String
        +liveProgress IndexingLiveProgress
    }

    class SearchEngine {
        -FileRepository repository
        -QueryParser parser
        +search(String) List~SearchResult~
    }

    class QueryParser {
        +parse(String) Query
    }

    class Query {
        <<record>>
        +type QueryType
        +value String
        +filters Map~String, String~
    }

    class SearchResult {
        <<record>>
        +path Path
        +filename String
        +extension String
        +preview String
        +modifiedAt LocalDateTime
        +sizeBytes Long
    }

    class IndexRun {
        <<record>>
        +id long
        +startedAt LocalDateTime
        +finishedAt LocalDateTime
        +rootPath String
        +totalFiles int
        +indexed int
        +skipped int
        +failed int
        +deleted int
        +elapsed Duration
    }

    Database ..|> FileRepository
    Database ..|> IndexRunRepository
    Indexer ..> FileRepository
    Indexer ..> IndexRunRepository
    Indexer ..> Crawler
    Indexer ..> Extractor
    BackgroundIndexer ..> Indexer
    BackgroundIndexer ..> IndexingJobSnapshot
    SearchEngine ..> FileRepository
    SearchEngine ..> QueryParser
    QueryParser ..> Query
```

### Important Domain Records

| Type | Purpose |
|---|---|
| `FileRecord` | File metadata discovered during crawling |
| `ExtractedRecord` | File metadata plus extracted content and preview |
| `SearchResult` | DTO returned by CLI/API search operations |
| `IndexReport` | Summary of a completed indexing execution |
| `IndexRun` | Persisted historical record of a past indexing run |
| `IndexingJobSnapshot` | Current or last-known background indexing state for the GUI |

---

## Runtime View

The runtime behavior of indexing is important because the GUI should not block during a long-running indexing operation; instead, it polls live state from the backend.

```mermaid
sequenceDiagram
    actor User
    participant GUI as Desktop GUI
    participant API as ApiServer
    participant BG as BackgroundIndexer
    participant IDX as Indexer
    participant FS as Local Filesystem
    participant DB as SQLite Database

    User->>GUI: Start indexing
    GUI->>API: POST /api/index/start
    API->>BG: start(indexerFactory)
    BG->>IDX: run asynchronously
    IDX->>FS: crawl files and read content
    IDX->>DB: write metadata, previews, full-text data
    loop while running
        GUI->>API: GET /api/index/status
        API-->>GUI: snapshot + live progress
    end
    IDX->>DB: persist index run history
    BG-->>API: completed snapshot
    GUI->>API: GET /api/index/history
    API-->>GUI: last index runs
```

---

## Deployment View

Although the system is local-first, it still has a meaningful deployment structure.

```mermaid
graph TD
    subgraph UserMachine["User Machine"]
        Electron["Electron Desktop App"]
        JavaServer["Java API Server Process"]
        JavaCli["Java CLI Process"]
        Sqlite[("index.db")]
        Files["Indexed Folders"]
    end

    Electron -->|localhost HTTP| JavaServer
    JavaServer --> Sqlite
    JavaServer --> Files
    JavaCli --> Sqlite
    JavaCli --> Files
```

Observations:
- The desktop app and API server are deployed separately even when started on the same machine.
- The database is embedded and local, which simplifies setup and removes the need for an external database server.
- The filesystem is not owned by the system; it is an external dependency that the system reads.

---

## Key Architectural Decisions

- **Local-first architecture**: all essential functionality runs on the user machine without remote infrastructure.
- **Two clients, one backend core**: the CLI and GUI both reuse the same Java domain logic.
- **API boundary for the GUI**: the frontend depends on stable HTTP endpoints rather than directly invoking Java code.
- **SQLite + FTS5**: appropriate for local full-text search because it has a low setup cost and portable persistence.
- **Repository abstraction**: keeps indexing and search logic decoupled from SQLite details.
- **Asynchronous indexing**: avoids blocking the GUI and enables progress reporting.
- **Persisted index history**: supports user feedback and future operational/reporting features.

---

## Conclusion

The proposed architecture separates user-facing clients, backend orchestration, domain logic, and persistence in a way that keeps responsibilities clear and supports incremental evolution. This structure allows the search engine to grow in features and usability while limiting the impact of change across the system.