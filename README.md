# Local File Search System

A local file search engine that indexes files on your machine and enables fast full-text and metadata search.

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
| `ext:java` | Filter by extension |
| `modified:2025-01-01` | Files modified after date |
| `size:1048576` | Files larger than size in bytes |
| `size:10kb`, `size:5mb`, `size:1gb` | Size filter with units (case-insensitive) |
| `config ext:json` | Combined full-text and metadata |

**Examples:**

```bash
java -jar ... search "getting started"
java -jar ... search "ext:java"
java -jar ... search "README.md"
java -jar ... search "size:10mb"
java -jar ... search "config ext:json" --limit 10
```

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

- Query parsing: full-text, filename, metadata, mixed input, and `size` unit parsing (`bytes`, `kb`, `mb`, `gb`)
- Search behavior: recursive traversal, single-word and multi-word full-text search
- Metadata filters: `ext`, `modified`, `size` (including unit forms)
- Runtime indexing options: `ignoreRules`, `maxFileSizeMb`, `previewLines`, `batchSize`
- Indexing lifecycle: background progress snapshot and final report
- Resilience: database failure propagation, unreadable files, and symlink-loop environments (platform-dependent skip)
- Incremental indexing: unchanged-file skip, modified-file update, and deleted-file cleanup

Typical output should report all tests passing, with one optional skipped test on platforms that cannot create symlinks.

---

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for a full C4 model of the system design.
