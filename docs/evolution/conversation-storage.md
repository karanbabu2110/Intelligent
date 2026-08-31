# Conversation Storage

Feature [#858](https://github.com/karanbabu2110/KAOS/issues/858) and Task
[#1084](https://github.com/karanbabu2110/KAOS/issues/1084) implement the first
durable persistence slice selected by Feature 004.01. A concrete
`SqliteConversationStore` in `io.kaos.conversation` stores stable positive
conversation identifiers in local SQLite and reads them in creation order.

## Implemented boundary

- `build.gradle.kts` pins Xerial SQLite JDBC `3.53.4.0` as a production
  dependency.
- A caller supplies the database path explicitly.
- The caller must provide an initialized `conversations` table with an
  auto-incrementing `sequence` and unique positive `identifier` contract.
- Each store operation uses an explicit transaction. A failed insert is rolled
  back and reported through a content-free `ConversationStorageException`.
- Reads order by creation sequence and return an immutable list capped at 1,000
  identifiers.

The store is a concrete package component, not a repository interface or a new
service. The foreground `ConversationSession` does not invoke it yet, and KAOS
does not choose or create a production database path. Temporary databases and
the minimal table are created only by deterministic tests.

## Capacity and safety evidence

The tests close and reopen a database before reading a stored identifier, keep
insertion order distinct from numeric order, and store 12 conversations. This
proves that durable collection capacity is not coupled to the current
eight-conversation foreground-session limit. A separate fixture inserts 1,001
records and verifies that one read returns only the first 1,000.

Null paths and non-positive identifiers fail before database work. A duplicate
identifier cannot add a second row, and unavailable-schema failures expose no
path, SQL, driver detail, or conversation content. The complete locked,
unreadable, corrupt, disk-full, and recovery policy remains owned by Feature
004.06.

## Deliberate exclusions

This task does not persist messages, initialize or migrate production schema,
select an application-data location, restore a session, replace current
in-memory limits, add a vector index, or wire storage into a command. Those
outcomes remain with Features 004.03 through 004.07.

## Validation

Run the deterministic storage suite with:

```powershell
./gradlew.bat test --tests 'io.kaos.conversation.SqliteConversationStoreTest' --no-daemon --warning-mode=all
```

Before completion, the task also runs the clean `verifyLocal` checkpoint,
dependency inspection, diff/link checks, and desktop/mobile architecture
validation. Rollback before production wiring is removal of the store, its
tests, and the SQLite JDBC dependency; no user database is currently created
by the application.

The selected driver version is verified against the official
[Xerial SQLite JDBC 3.53.4.0 release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.4.0).

Feature 004.03 subsequently extends this concrete store with bounded ordered
[message storage](message-storage.md).
