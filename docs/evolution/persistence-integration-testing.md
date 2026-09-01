# Persistence Integration Testing

Feature [#864](https://github.com/karanbabu2110/KAOS/issues/864) and Task
[#1089](https://github.com/karanbabu2110/KAOS/issues/1089) provide the final
Epic 004 evidence that local conversation persistence works through the complete
implemented application path. The feature was delivered by
[PR #40](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/pull/40).

## Verified boundary

Each scenario enters through the `conversation` command and uses the production
composition below:

```text
KaosApplication
  -> OllamaPromptClient
  -> ephemeral loopback HTTP server with streamed NDJSON
  -> SqliteConversationSchema and SqliteConversationStore
  -> temporary conversations.db
  -> restarted KaosApplication
```

The tests replace only external operator state: the Ollama endpoint is an
ephemeral loopback server, and the data path is a JUnit temporary directory.
They use the real request encoder, streaming decoder, schema, store,
transactions, restart restoration, and application output boundary.

## Demonstrated outcomes

The clean restart scenario:

1. submits one prompt through the real client;
2. streams and commits its assistant answer;
3. ends the first application run;
4. starts another conversation command against the same SQLite file; and
5. proves the next HTTP request contains the exact prior user and assistant
   messages followed by the new prompt, each exactly once and in order.

The failed partial-turn scenario:

1. commits one clean turn;
2. restarts and receives a visible answer prefix followed by malformed NDJSON;
3. verifies the private prompt, malformed content, and partial answer are absent
   from errors and durable history; and
4. restarts again and proves the next real HTTP request contains only the prior
   clean turn and current recovery prompt.

This demonstrates that SQLite is the authoritative restart source, clean pairs
cross the application boundary atomically, and failed partial AI output never
becomes future model context.

## Deterministic validation

Run only the complete local integration suite:

```powershell
./gradlew.bat test --tests 'io.kaos.app.KaosOllamaIntegrationTest' --no-daemon --warning-mode=all
```

Run the final repository checkpoint:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Neither command needs an installed Ollama model, the fixed production port,
external network access, operator conversation data, containers, PostgreSQL,
Redis, a background worker, or another process. The final clean merged-main
checkpoint passed all 11 `verifyLocal` tasks and 187 tests.

## Limits retained after Epic 004

The integration evidence does not add paging for conversations outside the
newest eight, exact last-selection persistence, deletion, trimming,
summarization, automatic rollover, provider-token estimation, automatic retry,
or automatic database repair. Those remain separate future outcomes rather
than hidden requirements of local persistence.
