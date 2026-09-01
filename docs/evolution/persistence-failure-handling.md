# Persistence Failure Handling

Feature [#862](https://github.com/karanbabu2110/KAOS/issues/862) and Task
[#1088](https://github.com/karanbabu2110/KAOS/issues/1088) make local SQLite
failures actionable without exposing private persistence details or changing
the existing one-process architecture.

## Failure boundary

`SqliteConversationSchema` and `SqliteConversationStore` classify SQLite
primary and extended result codes at the JDBC boundary. Only a stable
`ConversationStorageException.Reason` crosses into the application:

| Reason | Included conditions | Safe action |
| --- | --- | --- |
| `LOCKED` | busy or locked database | Close other database users, then retry deliberately |
| `CORRUPT` | corrupt, non-database, or invalid-format file | Stop and create an offline copy before diagnosis |
| `READ_ONLY` | read-only, permission, or authorization failure | Grant write access or select a writable directory |
| `CAPACITY` | disk full, memory, or database-size limit | Restore local capacity before retrying |
| `UNAVAILABLE` | cannot open, I/O, filesystem, or missing-resource failure | Verify the local directory and access |
| `INVALID_STATE` | schema, constraint, type, range, or API-state violation | Preserve a copy and verify the schema/state |
| `UNKNOWN` | no supported classification | Keep the database unchanged and inspect locally |

The public exception message contains no path, SQL, driver text, prompt, answer,
or stored content. Extended SQLite codes are reduced to their primary code so a
specific lock or read-only variant retains the correct recovery category.

## Runtime phases and data safety

The application reports where persistence stopped:

- Startup failure: no conversation session opens. KAOS does not delete,
  replace, or repair the database.
- `/new` failure: the new identifier is not saved and the command ends.
- Clean-turn write failure: the answer may already be visible, but the user and
  assistant pair is rolled back, not appended to memory, and explicitly
  reported as not saved before the command ends.

Every SQLite connection sets `busy_timeout` to zero. This feature intentionally
adds no lock waiting or automatic retry. Existing store transactions retain
their rollback behavior, so a partial pair is never accepted as a clean turn.

## Offline-copy recovery policy

Before any repair or forensic inspection:

1. stop KAOS and every other process using the conversation database;
2. locate the configured directory containing the fixed `conversations.db`;
3. copy `conversations.db` together with any adjacent
   `conversations.db-wal` or `conversations.db-shm` files;
4. keep the original files unchanged; and
5. diagnose or repair only the offline copy with a deliberately selected tool.

KAOS does not create an automatic backup, modify a corrupt file, replace a
database, delete records, retry a write, or select a repair tool. Those actions
could destroy the best remaining evidence and require explicit operator intent.

## Deterministic verification

Run the focused tests:

```powershell
./gradlew.bat test --tests 'io.kaos.conversation.*Test' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
```

The suite verifies every classification with deterministic SQLite result
codes, a real exclusive database lock with no wait, a real corrupt file that
remains byte-for-byte unchanged, invalid schema state, content-free recovery
messages, and application rollback after a synthetic write rejection. Portable
tests use synthetic result codes for disk-full and read-only conditions because
forcing the host filesystem into those states would be unsafe and unreliable.

Feature 004.07 now supplies the final epic-wide persistence integration suite.
It does not change this task's no-retry and offline-copy-first recovery policy.
