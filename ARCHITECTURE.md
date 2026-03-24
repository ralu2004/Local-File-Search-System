# Local File Search System - Architecture Overview

This document describes the architecture of the Local Search Engine, following the guidelines of the C4 model. It aims to provide a clear understanding of the system's structure, responsibilities, and boundaries.

---

## 1. System Context (Level 1)

The Local File Search Engine is a tool that runs entirely on the user's machine, indexing files and enabling fast content and metadata search.

```mermaid
graph TD
    User("👤 User")

    subgraph Machine["User's Machine"]
        Engine["Local File Search Engine"]
        FS["Local Filesystem"]
    end

    User -->|uses| Engine
    Engine -->|reads from| FS
```

| Actor / System | Role |
|----------------|------|
| **User** | Performs searches, triggers indexing, configures runtime options |
| **Local Filesystem** | Source of all indexed data — files, metadata, directory structure |
| **SQLite (DBMS)** | Embedded database storing indexed content and metadata |

---

## 2. Containers (Level 2)

```mermaid
graph TD
    User("👤 User")

    subgraph Machine["User's Machine"]
        CLI["CLI Application\n[Java Process]"]
        GUI["GUI Application\n[JavaScript + REST API]"]
        Core["Core Library\n[Java Library]"]
        DB[("SQLite Database\n[index.db]")]
    end

    User -->|uses| CLI
    User -->|uses| GUI
    CLI -->|delegates to| Core
    GUI -->|HTTP requests to| Core
    Core -->|reads/writes| DB
```

| Container | Technology | Responsibility |
|-----------|------------|----------------|
| **CLI Application** | Java + picocli | Parses commands and delegates to Core Library |
| **GUI Application** | JavaScript | Visual frontend communicating with Core via a local REST API |
| **Core Library** | Java | All domain logic — crawling, indexing, searching, and database access |
| **SQLite Database** | SQLite (FTS5) | Persistent storage of file metadata, content, and indexing history |

---

## 3. Components (Level 3)

Components of the **Core Library**. The library is designed to be independent of any frontend.

```mermaid
graph TD
    subgraph Core["Core Library"]
        Crawler["Crawler"]
        Indexer["Indexer"]
        Extractor["Extractor"]
        Search["Search Engine"]
        QueryParser["Query Parser"]
        DB["Database"]
        QueryBuilder["Query Builder"]
    end

    Crawler -->|pushes FileRecords| Indexer
    Indexer -->|delegates extraction| Extractor
    Extractor -->|returns ExtractedRecord| Indexer
    Indexer -->|writes via FileRepository| DB
    Search -->|parses input| QueryParser
    QueryParser -->|returns Query| Search
    Search -->|reads via FileRepository| DB
    DB -->|builds SQL| QueryBuilder
```

| Component | Responsibility |
|-----------|----------------|
| **Crawler** | Traverses the filesystem and emits `FileRecord` objects to the indexing flow |
| **Extractor** | Reads file content and produces text and preview strings |
| **Indexer** | Performs incremental checks and batch writes extracted records to storage |
| **Search Engine** | Accepts user queries and returns ranked results |
| **Query Parser** | Translates raw input strings into typed `Query` objects |
| **Database** | Implements `FileRepository` and `IndexRunRepository` — the only component with storage knowledge |
| **Query Builder** | Constructs SQL from `Query` objects, internal to the `db` package |

---

## 4. Classes (Level 4)

Key classes and interfaces. This section reflects the current implementation and will evolve across iterations.

```mermaid
classDiagram
    class FileRepository {
        <<interface>>
        +upsert(FileRecord, String, String)
        +batchUpsert(List~ExtractedRecord~)
        +search(Query, int) List~SearchResult~
        +getAllModifiedAtByPath() Map~Path,LocalDateTime~
    }

    class IndexRunRepository {
        <<interface>>
        +startIndexing(LocalDateTime) long
        +endIndexing(long, IndexReport)
        +getHistory() List~IndexRun~
    }

    class Database {
        -HikariDataSource dataSource
        -QueryBuilder queryBuilder
        +Database()
        +Database(String)
    }

    class Crawler {
        -Path root
        -List~PathMatcher~ matchers
        +crawl(Consumer~FileRecord~)
    }

    class Extractor {
        -int previewLines
        -long maxFileSize
        +extract(FileRecord) String
        +preview(FileRecord) String
    }

    class Indexer {
        -int batchSize
        +run() IndexReport
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
        +QueryType type
        +String value
        +Map~String,String~ filters
    }

    class QueryType {
        <<enumeration>>
        FULLTEXT
        FILENAME
        METADATA
        MIXED
    }

    Database ..|> FileRepository
    Database ..|> IndexRunRepository
    Indexer ..> FileRepository
    Indexer ..> IndexRunRepository
    Indexer ..> Crawler
    Indexer ..> Extractor
    SearchEngine ..> FileRepository
    SearchEngine ..> QueryParser
    QueryParser ..> Query
    Query ..> QueryType
```

### Model

| Class | Type | Key Fields |
|-------|------|------------|
| `FileRecord` | record | `path`, `filename`, `extension`, `sizeBytes`, `createdAt`, `modifiedAt` |
| `SearchResult` | record | `path`, `filename`, `extension`, `preview`, `modifiedAt` |
| `ExtractedRecord` | record | `record`, `content`, `preview` |
| `IndexReport` | record | `totalFiles`, `indexed`, `skipped`, `failed`, `deleted`, `elapsed` |
| `IndexRun` | record | `id`, `startedAt`, `finishedAt`, `totalFiles`, `indexed`, `elapsed` |
| `QueryType` | enum | `FULLTEXT`, `FILENAME`, `METADATA`, `MIXED` |

## Database

The database contains three logical areas:

- **File metadata** — path, filename, extension, size, timestamps
- **Full-text indexed content** — filename and file content searchable via FTS5
- **Indexing history** — per-run statistics including file counts and elapsed time

---

## Key Design Decisions

- **`FileRepository` and `IndexRunRepository` interfaces** — storage can be swapped without touching business logic
- **`QueryParser`** — query syntax evolves independently of search execution
- **`Crawler`** — traversal strategy changes without affecting the indexing pipeline
- **`Extractor`** — new file types can be supported without changing the indexer
- **SQLite + FTS5** — embedded database with built-in full-text search, no server required
- **GUI via REST API** — frontend is fully decoupled from the Core Library