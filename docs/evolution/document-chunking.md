# Document Chunking

Feature [#867](https://github.com/karanbabu2110/KAOS/issues/867) adds the
third bounded step of the First Knowledge and RAG Capability: split one exact
`ExtractedText` snapshot into deterministic retrieval units.

## User-visible outcome

Run the existing command with one local UTF-8 text file:

```powershell
./gradlew.bat --% run --args="knowledge-ingest \"D:\documents\notes.txt\""
```

Success reports the new chunk count without printing private document content:

```text
Ingested document: notes.txt (type: text/plain; charset=utf-8, bytes: 42, characters: 39, chunks: 1).
```

## Implemented policy

`KaosApplication` passes the immutable `ExtractedText` directly to
`DocumentChunker`. The chunker returns an immutable ordered list of
`DocumentChunk` values. Each chunk owns:

- the safe source file name;
- a zero-based index;
- inclusive start and exclusive end Unicode code-point offsets; and
- the exact text inside that range.

Each chunk contains at most 1,000 Unicode code points. Consecutive chunks share
exactly 200 code points unless the complete text fits in one chunk. The next
chunk therefore starts 800 code points after the previous chunk. Chunking uses
Java Unicode code-point offsets, so it never splits a supplementary character's
UTF-16 surrogate pair. It does not trim, normalize, add separators, or alter
line endings.

The existing 1,048,576-code-point extraction limit bounds one invocation to at
most 1,311 chunks. Both the chunks and their containing list are immutable.

## Privacy and lifecycle

Chunks exist only in the foreground command and become unreachable when the
command exits. The CLI prints the chunk count, not chunk text or offsets. There
is no network request, retry, cache, background work, persistence write,
embedding request, vector store, retrieval, or Ollama call.

## Deterministic evidence

```powershell
./gradlew.bat test --tests 'io.kaos.knowledge.*' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused suite proves one-chunk behavior, exact 1,000-code-point boundaries,
200-code-point overlap, supplementary Unicode safety, preserved whitespace and
line endings, the 1,311-chunk upper bound, immutable results, invalid-state
rejection, and privacy-safe command output.

## Deliberately deferred

This fixed character policy is the smallest deterministic boundary for the
first retrieval pipeline. It is not a provider-token estimate or a claim of
optimal semantic segmentation. Feature
[#868 — Embedding Generation](https://github.com/karanbabu2110/KAOS/issues/868)
owns the next transformation. Vector storage, retrieval, grounded prompts,
source attribution, and RAG evaluation remain later Epic 005 features.
