# Memory Retrieval

Feature [006.04](https://github.com/karanbabu2110/KAOS/issues/876) adds the
bounded exact-key read needed by later memory consumers.

## Implemented outcome

`AnswerDetailStore.retrieve()` returns either:

- `Optional.empty()` when `answer-detail` is absent; or
- exactly one validated `AnswerDetail` enum when it is present.

`AnswerDetailMemory` supplies the same contract for focused in-process tests.
`SqliteAnswerDetailStore` reads at most two ordered rows from version-1
`memory.db`, requires the sole row to use the owned `answer-detail` key, and
parses only `concise`, `balanced`, or `detailed`.

An unexpected key, invalid value, additional row, invalid schema, or database
failure becomes a content-free `MemoryStorageException`. Retrieval never
returns arbitrary database text and never silently treats malformed stored
state as absence.

## Boundaries

This in-process capability API is now consumed by the user-facing
`memory-inspect answer-detail` command implemented in Feature 006.06. Feature
006.05 also consumes it for one-shot Ollama prompt construction, but retrieval
itself does not change an AI request or contact Ollama.

Retrieval performs no write, retry, repair, migration, caching, embedding,
similarity search, vector indexing, logging, or remote transmission. Opening a
new configured database retains the version-1 initialization behavior selected
by Feature 006.03.

## Validation and handoff

Focused tests verify absent and present values in both implementations, reopen
retrieval from SQLite, exact enum parsing, duplicate protection, and rejection
of invalid values, unexpected keys, extra rows, and unsupported schemas. The
complete checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The ordered consumer is implemented by
[006.05 — Memory Use in AI Context](https://github.com/karanbabu2110/KAOS/issues/880).
It translates a retrieved value into one fixed KAOS-controlled instruction for
`ollama-prompt` while preserving the prior provider request when memory is
absent.
