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
| `--threads <n>` | CPU count | Number of extraction threads |

**Examples:**

```bash
# Basic index
java -jar ... index C:\Users\user\Documents

# With ignore rules
java -jar ... index C:\Users\user\Documents -i "*.log" -i "backup"

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
| `config ext:json` | Combined full-text and metadata |

**Examples:**

```bash
java -jar ... search "getting started"
java -jar ... search "ext:java"
java -jar ... search "README.md"
java -jar ... search "config ext:json" --limit 10
```

---

## Default ignore rules

The following are ignored automatically: `node_modules`, `target`, `build`, `dist`, `.git`, `.idea`, `AppData`, `Program Files`, `Windows`, and other common system directories.

---

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for a full C4 model of the system design.
