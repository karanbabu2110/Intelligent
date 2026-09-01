# Conversation Restore

Feature [#863](https://github.com/karanbabu2110/KAOS/issues/863) and Task
[#1087](https://github.com/karanbabu2110/KAOS/issues/1087) connect the existing
versioned SQLite schema and concrete store to the `conversation` command. The
demonstrable outcome is simple: a clean user/assistant turn completed in one
process is supplied as ordered Ollama history after the next process starts.

## Runtime flow

At conversation startup, `KaosApplication`:

1. resolves one local data directory and the fixed `conversations.db` filename;
2. creates the directory when needed and rejects an existing non-regular target;
3. initializes or validates SQLite schema version 1;
4. reads only the newest eight conversation identifiers, then each bounded
   ordered history;
5. reconstructs a `ConversationSession`, selects the newest restored
   conversation, and continues allocation above the greatest identifier in the
   complete durable collection; or
6. creates and stores conversation `1` when the database is empty.

`/new` creates the next foreground identifier and stores it. After a clean AI
result, the user and assistant messages commit as one SQLite batch before the
same turn is appended to the in-memory session. Failed, partial, cancelled, or
length-limited AI outcomes never enter either history. `/exit` ends only the
foreground process; durable records remain for the next run.

## Path and privacy boundary

The default path is `.kaos/conversations.db` below the Java user-home directory.
`kaos.conversation.data-directory` takes precedence over
`KAOS_CONVERSATION_DATA_DIRECTORY`; both select a directory while KAOS owns the
filename. Tests inject JUnit temporary paths and never use the operator's data.

The database contains exact local prompt and answer content, so filesystem
access to the chosen directory controls privacy. KAOS sends conversation content
only to the already-fixed loopback Ollama endpoint. Storage startup or write
failure ends the command with `KAOS-CONVERSATION-002`; the diagnostic contains
no path, SQL, driver detail, or conversation content. Feature 004.06 now
classifies the failure and explains whether startup, `/new`, or a post-answer
turn write failed. See [persistence failure handling](persistence-failure-handling.md).

## Bounds and deferred behavior

The durable collection can contain more than eight conversations and 512
messages, but one foreground process still loads at most the newest eight
conversations and 64 messages per conversation. Older durable conversations are
not yet pageable from the CLI. If eight restored conversations are loaded,
`/new` remains unavailable. The newest restored conversation is selected
deterministically; exact last-selection state is not stored in schema version 1.

Feature 004.06 supplies locked, corrupt, read-only, capacity, unavailable,
invalid-state, and unknown recovery categories. It deliberately performs no
automatic retry, backup, repair, replacement, or deletion. Feature 004.07 now
verifies the complete restart path through the real local integration boundary.
Naming, deletion, trimming,
summarization, automatic rollover, and provider-token estimation remain outside
this task.

## Deterministic verification

Run the focused path, session, store, and application tests:

```powershell
./gradlew.bat test --tests 'io.kaos.conversation.*Test' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
```

The application test runs the conversation command twice against one temporary
SQLite file and proves that the second prompt receives the first run's exact
clean user/assistant pair. Store tests prove newest-working-set order and
identifier allocation remains above records outside that working set.
