# Text Extraction

Feature [#866](https://github.com/karanbabu2110/KAOS/issues/866) adds the
second bounded step of the First Knowledge and RAG Capability: turn the exact
bytes admitted by Feature 005.01 into one immutable Unicode text snapshot.

## User-visible outcome

Run the existing command with one local UTF-8 text file:

```powershell
./gradlew.bat --% run --args="knowledge-ingest \"D:\documents\notes.txt\""
```

Success now proves both admission and extraction without printing private file
content:

```text
Ingested document: notes.txt (type: text/plain; charset=utf-8, bytes: 42, characters: 39).
```

`bytes` describes the admitted source. `characters` is the Unicode code-point
count of the extracted text, so one supplementary character such as an emoji
counts once rather than as two UTF-16 units.

## Implemented flow

`KaosApplication` passes the immutable `IngestedDocument` directly to
`PlainTextExtractor`. The extractor:

1. accepts only the implemented `text/plain; charset=utf-8` media type;
2. rechecks the one MiB source-byte boundary for callers that construct an
   `IngestedDocument` directly;
3. decodes UTF-8 strictly, without replacement characters;
4. preserves every decoded code point exactly, including a leading Unicode
   BOM, spaces, tabs, and original CR, LF, or CRLF line endings; and
5. returns `ExtractedText`, which owns the source name, exact immutable Java
   string, and a maximum of 1,048,576 Unicode code points.

The application reports only metadata. Extracted text remains in the foreground
command and becomes unreachable when the command exits.

## Failure and safety behavior

Direct extraction classifies unsupported media type, invalid/empty UTF-8 text,
and input above the admission limit without including the source name, bytes,
decoded content, or platform exception in the failure. The CLI maps any such
failure to `KAOS-KNOWLEDGE-004` with content-free recovery guidance.

There is no network request, retry, cache, background work, persistence write,
or Ollama call. The file path can still remain in shell history or local process
arguments as documented by Feature 005.01.

## Deterministic evidence

```powershell
./gradlew.bat test --tests 'io.kaos.knowledge.*' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused suite proves exact multilingual extraction, BOM and whitespace
preservation, Unicode code-point counting, strict malformed-input rejection,
media-type and size boundaries, content-free failures, and the application
metadata output.

## Deliberately deferred

Extraction produces one complete bounded text snapshot. It does not normalize,
split, overlap, label, or persist segments. Feature
[#867 — Document Chunking](https://github.com/karanbabu2110/KAOS/issues/867)
owns the next transformation. Embeddings, vector storage, retrieval, grounded
prompts, source attribution, and RAG evaluation remain later Epic 005 features.
