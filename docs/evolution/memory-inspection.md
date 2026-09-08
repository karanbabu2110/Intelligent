# Memory Inspection

Feature [006.06](https://github.com/karanbabu2110/KAOS/issues/878) adds the
first explicit read command for user-owned memory:

```text
memory-inspect answer-detail
```

## Implemented outcome

The command validates the fixed key before accessing storage and then uses the
existing bounded retrieval contract. It reports one of two successful states:

```text
Memory absent: answer-detail.
Memory: answer-detail=balanced.
```

A present value can only be `concise`, `balanced`, or `detailed` because the
SQLite retrieval boundary validates durable state before returning an
`AnswerDetail`. Inspection never prints a database path, SQL detail, arbitrary
row content, AI prompt, conversation, knowledge record, or provider response.

An unsupported key returns `KAOS-MEMORY-003` without opening the database or
echoing the rejected value. Invalid or unavailable SQLite state returns the
existing content-free `KAOS-MEMORY-002` recovery guidance. Absence is an
expected state and therefore exits successfully.

## Boundaries

Inspection performs no edit, delete, create, repair, retry, migration, broad
listing, semantic search, embedding, AI request, or remote call. Opening a new
configured `memory.db` retains the existing version-1 initialization behavior;
no new schema or persistence technology is introduced.

## Validation

Focused command tests verify present, absent, invalid-key, and storage-failure
outcomes. Router and application tests verify the exact public syntax,
content-safe rejection of extra arguments, and absent-to-present inspection
across separate application runs backed by real SQLite.

The complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Feature 006.07 owns explicit editing and deletion. This feature does not mutate
the inspected memory.
