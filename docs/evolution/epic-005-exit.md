# Epic 005 Exit — First Knowledge and RAG Capability

Epic [005](https://github.com/karanbabu2110/KAOS/issues/11) delivers one local
plain-text knowledge path from document admission through a grounded answer.

## Verified outcome

The single KAOS application now:

1. admits one bounded regular UTF-8 `.txt` document;
2. preserves its exact text and creates deterministic overlapping chunks;
3. generates embeddings through explicitly configured loopback Ollama;
4. commits the document, model identity, chunks, and vectors atomically to
   version-1 SQLite storage;
5. embeds a question and deterministically ranks up to three compatible chunks;
6. constructs one bounded injection-aware prompt with stable source labels;
7. submits that exact prompt to the configured local chat model;
8. streams the answer once and in order; and
9. prints the included source coordinates only after clean completion.

All ten Epic 005 features are closed as completed: #865–#873 and #1091. Their
delivery PRs are #43–#52.

## Automated evidence

The final Feature 005.10 tree was validated before merge and matched merged
`main` exactly. `./gradlew.bat clean verifyLocal --no-daemon
--warning-mode=all` passed all 11 tasks with 254 passing tests, zero failures or
errors, and one existing skip.

Focused command tests prove exact prompt construction and transfer, ordered
answer output, citation ordering, unavailable-context handling, and suppression
of citations after partial provider failure. The HTTP integration test crosses
the application, real Ollama client, loopback streaming protocol, and temporary
SQLite knowledge store.

## Installed-model demonstration

On 2026-09-07, the installed distribution ran against Ollama 0.32.1 with:

- `embeddinggemma:latest` (`85462619ee72`, 768 dimensions); and
- `qwen3:4b-instruct` (`0edcdef34593`).

An isolated temporary data directory admitted this synthetic document:

```text
KAOS backup policy: backups run nightly at 02:00 UTC. Backup retention is 30 days.
```

`knowledge-ingest` returned exit 0 and committed one document, one chunk, and
one embedding. `knowledge-ask "When do KAOS backups run?"` returned exit 0:

```text
KAOS backups run nightly at 02:00 UTC [1].
Citation sources: 1.
citation [1]: document: 1, source: policy.txt, chunk: 0
```

The demonstration used synthetic content and a temporary SQLite database. It
did not expose user documents or modify the repository.

## Known limitations

- Only regular UTF-8 `.txt` files up to 1 MiB are supported.
- Retrieval has no relevance threshold or reranking and returns at most three
  compatible chunks.
- Citation labels identify evidence supplied to the model. KAOS does not
  validate generated claims or reject omitted, invented, or incorrect labels.
- Knowledge answers are not part of conversation history and are not persisted.
- Both embedding and answer generation require explicitly selected local Ollama
  models; KAOS never downloads a model automatically.

These limitations do not prevent the bounded epic outcome. They remain explicit
future candidates and are not prerequisites for closing Epic 005.

## Next checkpoint

[Epic 006 — First Memory Capability](https://github.com/karanbabu2110/KAOS/issues/10)
is the next ordered evolutionary epic under #814. It remains in Backlog and is
not active until the user continues after this exit checkpoint.
