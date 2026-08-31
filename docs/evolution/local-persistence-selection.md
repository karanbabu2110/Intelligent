# Local Persistence Selection

Feature [#859](https://github.com/karanbabu2110/KAOS/issues/859) and Task
[#1083](https://github.com/karanbabu2110/KAOS/issues/1083) select local SQLite
as the authoritative store for the first KAOS conversation persistence
implementation. This is an architecture decision; no driver, schema, database
file, or persistence behavior exists yet.

## Decision

Keep persistence in the single Java process and the existing
`io.kaos.conversation` capability. Store conversations and their ordered
messages in one local SQLite database through JDBC. Use transactions for each
complete state change and targeted reads rather than rewriting or loading the
entire conversation collection.

SQLite is the source of truth. A vector index may be evaluated later for
semantic search or RAG, but it must be derived and rebuildable from the
authoritative records; embeddings must not replace exact conversation content.

## Current evidence and clarified growth requirement

The current implementation deliberately protects one foreground process with
limits of 8 conversations, 64 messages per history, and 65,536 Unicode code
points per message. Those are present runtime safety controls, not permanent
product-capacity targets. The intended persisted collection must be able to
grow beyond 8 conversations and 512 total messages.

The application still has one process and one user, but future storage needs:

- targeted conversation and ordered-message access;
- insertion without rewriting every earlier conversation;
- transactional conversation/message changes;
- schema evolution and restore across application versions; and
- bounded paging so database growth does not imply unbounded memory or Ollama
  context.

SQLite supplies the local transactional boundary. The Xerial SQLite JDBC
project provides Java JDBC access in a single dependency and packages native
libraries for major operating systems. The exact driver version is deliberately
selected and verified in Feature 004.02, not added by this decision.

## Options considered

| Option | Benefit | Current cost or mismatch | Decision |
| --- | --- | --- | --- |
| Local SQLite | Transactions, targeted access, ordered rows, schema evolution, growth without whole-dataset rewrites | Adds a JDBC driver, SQL schema, migration, locking, and database recovery responsibilities | Select |
| Versioned JSON snapshot | Existing Jackson dependency, inspectable, portable | Rewrites and validates the whole collection; unsuitable once retained conversations are expected to grow | Defer only for export/backup evaluation |
| Vector database | Semantic similarity search | Embeddings are lossy derived data and do not preserve authoritative ordered messages or ordinary transactions | Defer to RAG/search features |
| Java object serialization | Small initial code surface | Opaque, Java-type-coupled, brittle compatibility, and unsafe input history | Reject |
| Custom text or NDJSON | Human-readable append options | Escaping, indexing, updates, partial-record recovery, and schema rules become a custom database protocol | Reject |

## Selected ownership and data boundary

The future database will contain persistence-owned representations of:

- schema metadata and version;
- conversations with stable identifiers and deterministic creation order;
- ordered messages with role and exact content; and
- the state needed to resume selection or identifier allocation when later
  features require it.

Rows must be mapped to validated conversation domain objects. SQL, JDBC, table,
column, and migration concerns must not be added to `ConversationMessage`,
`ConversationHistory`, or `ConversationSession` merely for storage convenience.
The exact tables, indexes, database path, driver version, page/query sizes, and
schema version belong to Features 004.02 through 004.04.

Persisted collection growth does not remove operational bounds. Individual
message size, database growth, transaction size, page size, loaded working set,
and provider context must remain explicit and independently bounded. The
current 8-conversation and 32-turn foreground limits remain implemented until a
later roadmap issue deliberately replaces them with safe paging/lifecycle
behavior.

## Guarded safety review

The affected asset is user-owned conversation content on the local machine.
The user running KAOS supplies the authority to read and write the later local
database. No network transmission, credential, shared account, or remote store
is selected.

Later implementation must:

- restrict database access to one explicitly resolved application-data path;
- reject path escape or unexpected non-regular-file targets where applicable;
- use transactions so incomplete conversation/message changes roll back;
- enable and verify SQLite integrity and foreign-key behavior appropriate to
  the selected schema;
- bound database bytes, transaction size, read pages, identifiers, roles, and
  message content before constructing live state;
- handle unreadable, locked, corrupt, unsupported, or migration-failed state
  without silently replacing user data;
- keep prompts, messages, SQL values, raw database content, and exception
  details out of logs and user diagnostics; and
- provide explicit backup/recovery guidance before destructive repair or
  migration.

SQLite provides atomic transaction semantics, but it does not remove the need
for application validation, backups, migration tests, or disk-full and
permission failure handling.

Encryption at rest is not selected implicitly: KAOS has no key-management or
credential capability. Users must treat the future database as private local
data and rely on operating-system account and disk protections until a later
evidence-backed privacy feature defines more.

## Feature boundaries

- Feature 004.02 owns the first conversation storage behavior and driver
  dependency decision.
- Feature 004.03 owns ordered message storage behavior.
- Feature 004.04 owns tables, indexes, schema versions, and migrations.
- Feature 004.05 owns restoring state into the application lifecycle.
- Feature 004.06 owns observable database failure and recovery behavior.
- Feature 004.07 owns complete persistence integration evidence.

This task does not choose an exact path or driver version, define SQL, perform
database I/O, remove the current runtime safety limits, or create a storage
interface before its first implementation consumer exists.

## Validation and revisit triggers

The decision is validated against current source ownership, the clarified
growth requirement, every Epic 004 feature boundary, Gradle dependencies, and
package-extraction rules. SQLite documents atomic commit for transactions, and
the Xerial project documents JDBC access and its packaged platform libraries:

- [SQLite atomic commit](https://www.sqlite.org/atomiccommit.html)
- [Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc)

Repository links, formatting, the clean application checkpoint, and the living
architecture are verified before completion.

Reconsider another relational store only if multiple processes, remote access,
independent deployment, or measured scale exceeds an embedded database.
Evaluate a vector index only when semantic retrieval has an explicit consumer
and rebuild/consistency rules. Because no runtime code or data exists in this
feature, rollback is deletion of this decision before Feature 004.02 begins.

Conversation Storage Feature
[#858](https://github.com/karanbabu2110/KAOS/issues/858) is the next approved
implementation checkpoint after this decision is reviewed and merged.
