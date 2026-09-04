# Embedding Generation

Feature [005.04](https://github.com/karanbabu2110/KAOS/issues/868) adds the first
bounded embedding step to the existing `knowledge-ingest` command.

## Implemented outcome

After admission, exact extraction, and deterministic chunking, the command
loads one explicit `KAOS_OLLAMA_EMBEDDING_MODEL` selection and submits every
chunk in order to fixed loopback Ollama `POST /api/embed`. Each request includes
one exact chunk and `truncate: false`. KAOS validates the returned model, exactly
one vector, finite numeric values, a non-empty maximum of 4,096 dimensions, and
the same dimension count for every chunk.

The successful command reports safe metadata and counts only. It never prints
chunk content, vector values, the configured model, a path, or a provider body.
Configuration, availability, rejection, timeout, cancellation, invalid response,
and local response-limit failures produce stable content-free recovery guidance.

## Boundaries and ownership

- `OllamaEmbeddingClient` owns fixed-loopback HTTP, a shared five-minute
  foreground deadline, and a 128 KiB response limit per chunk.
- `OllamaEmbeddingConfiguration` owns the explicit embedding-model selection.
- `EmbeddedChunk` pairs one existing immutable chunk with a defensively copied,
  bounded vector.
- `KnowledgeIngestCommand` coordinates the in-process sequence and discards the
  complete working set when the command exits.

No default model, model download, retry, remote endpoint, persistence, vector
index, similarity search, retrieval, grounded prompt, or citation exists.
Feature 005.05 owns vector storage.

## Verification

Focused tests use deterministic loopback HTTP and prove ordered exact input,
disabled truncation, immutable vectors, configuration rejection, dimension
consistency, malformed-response handling, and privacy-safe command failures:

```powershell
./gradlew.bat test --tests "io.kaos.ai.ollama.OllamaEmbedding*" --tests "io.kaos.knowledge.EmbeddedChunkTest" --tests "io.kaos.app.KnowledgeIngestCommandTest" --no-daemon --warning-mode=all
```

The final checkpoint is:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```
