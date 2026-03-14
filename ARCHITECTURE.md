# Local File Search System - Architecture Overview

This document describes the architecture of the Local Search Engine, following the guidelines of the C4 model.
It aims to provide a clear understanding of the system's structure, responsibilities, and boundaries.

## 1. System Context (Level 1)

The Local File Search Engine is a tool that runs on the user's machine. It indexes local files and allows the user to search them by filename, content and metadata. It extracts metadata and content from files, stores them in a database, and provides fast search capabilities with contextual previews.

```mermaid
graph TD
    User("User")

    subgraph Machine["User's Machine"]
        Engine["Local File Search Engine"]
        FS["Local Filesystem"]
    end

    User -->|uses| Engine
    Engine -->|reads from| FS
```

### Primary Actor

- **User** 
Performs search queries, configures runtime options, and views the results.

### External Systems

- **Operating System (Filesystem)**
Provides access to directories, file metadata and content. The search engine relies on the OS for recursive traversal, safe handling of permissions, symbolic links and file types.

- **Database Management System (DBMS)**
Stores indexed file metadata (size, timestamps, extensions etc.) and contents.
A lightweight, embedded relational database is used, such as SQLite, to avoid server overhead, while supporting efficient full-text search.

### System Responsibilities
- Crawl directories recursively and collect file metadata
- Extract content from supported files
- Store metadata and content in the database
- Execute single and multi-word search queries
- Generate contextual previews for search results
- Handle errors (permissions, symlink loops, corrupted files)
- Provide a responsive interface for indexing and searching

## 2. Containers (Level 2)

The system comprises four containers. 
```mermaid
graph TD
    User(" User")

    subgraph Machine["User's Machine"]
        CLI["CLI Application\n[Java Process]"]
        GUI["GUI Application\n[Java, TBD - planned]"]
        Core["Core Library\n[Java Library]"]
        DB[("SQLite Database\n[search.db]")]
    end

    User -->|uses| CLI
    User -->|uses| GUI
    CLI -->|delegates to| Core
    GUI -->|delegates to| Core
    Core -->|reads/writes| DB
```

| Container | Technology | Responsibility |
|-----------|------------|----------------|
| **CLI Application** | Java | Thin frontend — parses commands and arguments, delegates to Core Library, displays results |
| **GUI Application** | TBD *(planned)* | Visual frontend for search and results — design and framework TBD |
| **Core Library** | Java | All core logic — crawling, indexing, searching, and database access |
| **SQLite Database** | SQLite (FTS5) | Persistent storage of file metadata and full-text content |

## 3. Components (Level 3)

This level breaks down the **Core Library** container into several components and their relationships.
The Core Library is responsible for all main logic — crawling, extracting, indexing, searching, and database access — and is designed to be independent of any frontend.

```mermaid
graph TD
    subgraph Core["Core Library"]
        Crawler["Crawler"]
        Indexer["Indexer"]
        Extractor["Extractor"]
        Search["Search Engine"]
        DB["DB Manager"]
    end

    Crawler -->|yields FileRecords| Indexer
    Indexer -->|delegates extraction| Extractor
    Extractor -->|returns extracted content| Indexer
    Indexer -->|stores records| DB
    Search -->|queries| DB
```

| Component | Responsibility |
|-----------|----------------|
| **Crawler** | Recursively walks the filesystem, applies ignore rules, and yields file records with metadata |
| **Indexer** | Orchestrates the indexing pipeline — receives file records from the Crawler, delegates content extraction to the Extractor, stores results via the DB Manager, tracks progress, and generates a report |
| **Extractor** | Reads file content and produces a normalized text representation. Isolated as a separate component to encapsulate file-type-specific logic, making it straightforward to support additional formats in future iterations |
| **Search Engine** | Accepts user queries, builds FTS5 queries, and returns ranked results with contextual previews |
| **DB Manager** | Owns the SQLite connection and handles all reads and writes, isolating the rest of the system from database concerns |