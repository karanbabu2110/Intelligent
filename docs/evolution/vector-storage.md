# Vector Storage

Feature [005.05](https://github.com/karanbabu2110/KAOS/issues/869) persists one
complete knowledge-ingestion result in local SQLite.

## Implemented outcome

After all exact chunks receive valid embeddings, `KnowledgeIngestCommand`
opens fixed `knowledge.db`, validates schema version 1, and commits one document
row plus every ordered chunk in one transaction. Stored data includes the safe
source name, explicit embedding-model identity, code-point offsets, exact chunk
text, and vectors encoded as fixed-width big-endian doubles.

Success reports only the generated document identifier and existing safe counts.
The store can reopen the database and restore one bounded document with exact
chunk order and vector values. Unsupported schemas and failed writes are
preserved and reported through content-free locked, corrupt, read-only, capacity,
unavailable, invalid-state, or unknown recovery categories.

## Ownership and limits

`KAOS_KNOWLEDGE_DATA_DIRECTORY` selects the parent directory; the system property
`kaos.knowledge.data-directory` takes precedence. Otherwise KAOS uses
`%USERPROFILE%\.kaos\knowledge.db` on Windows. KAOS owns the filename and creates
the directory when needed.

One write contains 1–1,311 chunks with one consistent source, sequential indexes,
and one consistent vector dimension of 1–4,096. There is no retry, repair,
migration, replacement, deletion, deduplication, retention policy, vector index,
similarity calculation, or retrieval. Feature 005.06 owns relevant-context retrieval.

## Verification

```powershell
./gradlew.bat test --tests "io.kaos.knowledge.SqliteKnowledgeStoreTest" --tests "io.kaos.knowledge.KnowledgeDatabasePathTest" --tests "io.kaos.app.KnowledgeIngestCommandTest" --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```
