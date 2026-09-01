# SQLite Schema Management

Feature [#860](https://github.com/karanbabu2110/KAOS/issues/860) and Task
[#1086](https://github.com/karanbabu2110/KAOS/issues/1086) add the first
production-owned schema lifecycle for local conversation persistence.
`SqliteConversationSchema.initialize` accepts an explicit caller-owned path,
initializes an empty SQLite database transactionally as version 1, and validates
the required structure whenever that version is reopened.

## Version 1 contract

Schema version 1 uses SQLite `PRAGMA user_version` and owns:

- `conversations`, ordered by an auto-incrementing sequence with a unique
  positive identifier contract enforced by the store and database;
- `messages`, ordered by sequence with required conversation identifier, role,
  content, and a foreign key to `conversations.identifier`; and
- `messages_conversation_sequence_idx` for targeted ordered message reads.

Version 0 means only an empty database. The initializer refuses to adopt an
unversioned database containing application objects because their ownership and
compatibility are unknown. Version 1 is idempotent after validation. A newer
version, missing columns, missing identifier uniqueness, missing foreign key, or
missing message-read index fails without mutation.

There is no earlier production schema to migrate. Creating version 1 from an
empty version-0 database is the only current migration. Later migrations must
be added from real released versions and verified with preserved data rather
than speculative migration infrastructure.

## Safety and ownership

Initialization uses one explicit transaction and rolls back failed DDL. Public
failure text does not expose database paths, SQL, content, or driver details.
Tests use only JUnit temporary directories and prove empty initialization,
idempotent reopen with stored data, index and foreign-key structure, rejection
of conflicting or incomplete schemas, unsupported-version preservation, and
unavailable-path diagnostics.

The schema initializer and store remain concrete classes in
`io.kaos.conversation`. Feature 004.05 now invokes them directly from the
conversation application lifecycle for restart restore. Feature 004.06 now
classifies persistence failures and supplies privacy-safe, offline-copy-first
recovery without automatic retry, repair, replacement, or deletion.

## Validation

Run the focused schema and store tests with:

```powershell
./gradlew.bat test --tests 'io.kaos.conversation.SqliteConversationSchemaTest' --tests 'io.kaos.conversation.SqliteConversationStoreTest' --no-daemon --warning-mode=all
```

Before completion, run `clean verifyLocal`, local-link and diff checks, plus the
desktop/mobile living-architecture validation. The initializer is now part of
the production conversation startup path; Feature 004.05 records that integration
and its rollback boundary.
