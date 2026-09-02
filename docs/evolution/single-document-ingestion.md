# Single Document-Type Ingestion

Feature [#865](https://github.com/karanbabu2110/KAOS/issues/865) begins the
First Knowledge and RAG Capability with one deliberately small outcome:
Task [#1090](https://github.com/karanbabu2110/KAOS/issues/1090) admits one
bounded local plain-text document into an immutable in-memory snapshot.

## User outcome

From the repository root, ingest one local file:

```powershell
./gradlew.bat --% run --args="knowledge-ingest \"D:\documents\notes.txt\""
```

A successful command prints only the file name, media type, and exact byte
count, then exits. For example:

```text
Ingested document: notes.txt (type: text/plain; charset=utf-8, bytes: 42).
```

The path is supplied as a process argument and can remain in shell history or
be visible to local process inspection. Do not place secrets in the path. KAOS
does not print the absolute path or document content.

## Implemented boundary

`KaosApplication` routes `knowledge-ingest <path>` directly to
`TextDocumentIngestor` in the `io.kaos.knowledge` package. The ingestor:

1. accepts exactly one file whose name ends in `.txt`, case-insensitively;
2. rejects blank or terminal-control file names, directories, symbolic links,
   missing files, empty files, and files larger than 1,048,576 bytes;
3. reads through a capped foreground stream and probes for growth without
   retaining an extra content byte;
4. validates strict UTF-8 without replacing malformed input; and
5. returns an `IngestedDocument` that defensively copies the exact source
   bytes and reports `text/plain; charset=utf-8`.

The admitted bytes live only for the command. There is no document database,
background work, watcher, retry, repair, deletion, or external request.

## Failure and privacy behavior

| Code | Meaning | Recovery |
| --- | --- | --- |
| `KAOS-KNOWLEDGE-001` | Unsupported type, empty/non-regular/link input, invalid name, or malformed UTF-8 | Choose one readable, non-empty UTF-8 `.txt` file |
| `KAOS-KNOWLEDGE-002` | Missing or inaccessible local file | Check local existence and access, then retry |
| `KAOS-KNOWLEDGE-003` | More than 1 MiB | Choose a smaller file |

Errors contain no file name, path, content, platform exception, or stack trace.
The command reads local user-selected data only and makes no network request.

## Deterministic evidence

```powershell
./gradlew.bat test --tests 'io.kaos.knowledge.*' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The focused suite uses JUnit temporary files to prove exact multilingual UTF-8
bytes, defensive copying, the exact 1 MiB boundary, oversize rejection, empty
and invalid UTF-8 rejection, type checks, directory and missing-file behavior,
symbolic-link rejection where the platform permits link creation, CLI routing,
safe metadata, and content-free errors.

## Deliberately deferred

This feature does not expose decoded text to a consumer, extract another file
format, persist source documents, chunk text, generate embeddings, select a
vector store, retrieve context, build a grounded prompt, cite sources, or call
Ollama. Those outcomes remain with Features 005.02 through 005.09. The next
approved checkpoint after this feature is
[Feature 005.02 — Text Extraction](https://github.com/karanbabu2110/KAOS/issues/866).
