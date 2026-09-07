# Memory Storage

Feature [006.03](https://github.com/karanbabu2110/KAOS/issues/877) makes the
fixed `answer-detail` preference durable across application processes.

## Implemented outcome

`memory-create` resolves a fixed `memory.db` path, initializes or validates
schema version 1, and inserts the already validated key and enum value through
the existing Xerial SQLite JDBC dependency. The primary key makes creation
atomic and create-only: a later process cannot overwrite an existing value.

The data directory is selected in this order:

1. `kaos.memory.data-directory` system property;
2. `KAOS_MEMORY_DATA_DIRECTORY` environment variable; or
3. the local user's `.kaos` directory.

KAOS owns the `memory.db` filename. Configuration selects only its parent
directory. No database path, SQL text, rejected input, or unrelated record is
printed by normal diagnostics.

## Schema and failure boundary

Schema version 1 contains one `answer_detail_memory` table with a non-null text
primary key and non-null structured value. Initialization and schema checks are
transactional. An empty database may be initialized; an unknown schema or
version is preserved and rejected without repair or replacement.

Storage failures are mapped to content-free locked, corrupt, read-only,
capacity, unavailable, invalid-state, or unknown reasons. The command reports
stable `KAOS-MEMORY-002` recovery guidance. There is no retry, migration,
background write, remote transmission, or automatic collection.

## Why SQLite, not a vector database

KAOS already uses a pinned SQLite JDBC driver for local transactional state.
The first memory has one exact key and three values, so it needs exact lookup
and atomic state changes—not embeddings or semantic similarity. A vector
database would add derived data, indexing, another lifecycle, and recovery work
without serving this use case. It should be evaluated only if later evidence
introduces many free-form memories that require semantic retrieval.

## Validation and handoff

Focused tests verify exact persistence, reopen behavior, duplicate protection,
invalid-input rollback, path precedence, unsupported-schema preservation, safe
failure reporting, and separate application runs. The complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

This feature did not itself expose retrieval or inspection, apply memory to an
AI request, or implement editing or deletion. Feature 006.04 subsequently added
bounded exact-key [Memory Retrieval](memory-retrieval.md) without broadening the
memory domain.
