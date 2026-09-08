# KAOS

KAOS is one evolving Java application, delivering one useful goal at a time.
The development-model reset is complete; capabilities now grow incrementally
inside the verified single application.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Completed epics: [Epic 000 — Development Model Reset](https://github.com/karanbabu2110/KAOS/issues/815), [Epic 001 — Minimal KAOS Application](https://github.com/karanbabu2110/KAOS/issues/2), [Epic 002 — First AI Integration](https://github.com/karanbabu2110/KAOS/issues/3), [Epic 003 — Conversation Capability](https://github.com/karanbabu2110/KAOS/issues/9), [Epic 004 — Local Persistence](https://github.com/karanbabu2110/KAOS/issues/823), and [Epic 005 — First Knowledge and RAG Capability](https://github.com/karanbabu2110/KAOS/issues/11)
- Active epic: [Epic 006 — First Memory Capability](https://github.com/karanbabu2110/KAOS/issues/10); [Feature 006.09 — Memory Testing and Evaluation](https://github.com/karanbabu2110/KAOS/issues/882) proves the complete durable `answer-detail` lifecycle through real application and loopback Ollama boundaries
- Repository state: one root Gradle/Java 21 application with one production entry point, explicit bounded memory creation, inspection, editing, deletion, and privacy reporting, exact validated retrieval, and one-shot AI-context use through version-1 local SQLite, bounded UTF-8 document admission, exact extraction and overlapping chunks, explicit local Ollama embeddings, atomic version-1 SQLite knowledge storage, deterministic top-three cosine retrieval, bounded injection-aware grounded prompt construction, streamed local grounded answers with stable source citations, deterministic cross-boundary evaluation, bounded persistent conversations, explicit model selection, Jackson JSON and pinned SQLite JDBC runtime libraries, and classified privacy-safe failures
- Completed features, stories, tasks, and verified evidence: [completed work and evidence](docs/evolution/completed-work-and-evidence.md)

## Architecture

The [living KAOS architecture website](ui/architecture/index.html) shows the
verified current runtime, the next approved capability, future capability
candidates, and the evidence required before introducing modules or services.
It is a structured, buildless UI that can grow into multiple pages or an
application when real complexity justifies that evolution. See its
[local run and maintenance guide](ui/architecture/README.md).

Every feature pull request that changes packages, dependencies, integrations,
data ownership, or runtime boundaries must update the diagram. Implemented and
planned elements must remain visually distinct.

## Developer guide

Start with the [KAOS developer guide](docs/development/developer-guide.md) for
prerequisites, setup, application commands, focused and complete test commands,
build outputs, troubleshooting, and the feature delivery workflow.

## Version

The current cumulative release checkpoint is **1.3.0**, identified by annotated
tag and [GitHub Release `v1.3.0`](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/releases/tag/v1.3.0).

This release represents all verified work through Epic 005: the evolutionary
development model, minimal runnable application, local AI, bounded persistent
conversations, and a complete first local Knowledge and RAG path from text-file
admission through a streamed grounded answer with source citations. It is a
capability milestone, not a production-readiness, answer-correctness, or
permanent database-compatibility claim. See the
[1.3.0 release notes](docs/releases/v1.3.0.md).

## Run the application

```powershell
./gradlew.bat run --args=status
```

The no-argument form remains supported. Both forms print
`KAOS application baseline is running.` and exit successfully. Run
`./gradlew.bat run --args=help` for the exact supported syntax. Unknown
commands or extra arguments produce safe guidance and a nonzero result without
echoing the supplied values.

Admit one local UTF-8 plain-text document into a bounded in-memory snapshot:

```powershell
$env:KAOS_OLLAMA_EMBEDDING_MODEL = "embeddinggemma"
$env:KAOS_KNOWLEDGE_DATA_DIRECTORY = "D:\kaos-data"
./gradlew.bat --% run --args="knowledge-ingest \"D:\documents\notes.txt\""
```

The command accepts one non-empty regular `.txt` file, rejects symbolic links,
and enforces a 1 MiB limit. It strictly extracts the admitted UTF-8 bytes without
normalizing their decoded content, then splits the exact text into immutable
1,000-code-point chunks with 200-code-point overlap. Using the explicitly
selected installed embedding model, it sends each chunk in order to fixed
loopback Ollama `/api/embed` with provider truncation disabled and validates one
finite vector of at most 4,096 dimensions per chunk. It prints only safe metadata
and counts. Errors do not expose the path, content, model name, or provider body.
The complete document, chunks, embedding model identity, and vectors commit
atomically to version-1 local SQLite `knowledge.db`; success includes the safe
stored-document identifier. See [single document ingestion](docs/evolution/single-document-ingestion.md),
[text extraction](docs/evolution/text-extraction.md), and
[document chunking](docs/evolution/document-chunking.md), then
[embedding generation](docs/evolution/embedding-generation.md), then
[vector storage](docs/evolution/vector-storage.md) for exact behavior and limitations.

Retrieve the three most relevant compatible stored chunks without printing their
content:

```powershell
$env:KAOS_OLLAMA_EMBEDDING_MODEL = "embeddinggemma"
$env:KAOS_KNOWLEDGE_DATA_DIRECTORY = "D:\kaos-data"
./gradlew.bat --% run --args="knowledge-retrieve \"What does the document say about caching?\""
```

The query is limited to 1,000 Unicode code points and embedded through the same
fixed loopback endpoint. KAOS compares only vectors with the same explicit model
and dimensions, ranks by cosine similarity, and prints at most three document,
source, chunk, and score references. It then constructs one prompt of at most
4,096 code points from the exact question and as many whole ranked contexts as
fit. Every included record receives the stable label `[1]`, `[2]`, or `[3]`;
the command prints a content-free citation manifest after the prompt counts.
It does not print the query, stored chunk content, or constructed prompt. See
[relevant-context retrieval](docs/evolution/relevant-context-retrieval.md) and
[grounded prompt construction](docs/evolution/grounded-prompt-construction.md),
then [source attribution](docs/evolution/source-attribution.md).
The deterministic [RAG evaluation](docs/evolution/rag-evaluation-testing.md)
exercises extraction through citation construction without an external model.

Answer one question from the stored local knowledge and print the sources made
available to the answer:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_EMBEDDING_MODEL = "embeddinggemma"
$env:KAOS_KNOWLEDGE_DATA_DIRECTORY = "D:\kaos-data"
./gradlew.bat --% run --args="knowledge-ask \"When do backups run?\""
```

KAOS submits the exact bounded grounded prompt to fixed loopback Ollama, streams
the answer, and prints source coordinates only after clean completion. Failed or
partial responses never print citations. See
[grounded answer generation](docs/evolution/grounded-answer-generation.md).

Check whether Ollama is reachable on the fixed local endpoint
`http://127.0.0.1:11434/api/version`:

```powershell
./gradlew.bat run --args=ollama-status
```

A successful check prints only the validated Ollama version. The command sends
no prompt, model name, credential, personal data, or file content. If Ollama is
unavailable or its version response is invalid, KAOS returns a safe
`KAOS-AI-001` error with recovery guidance.

Select and inspect the model that later AI commands will use:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat run --args=ollama-model
```

KAOS requires an explicit selection and does not assume or download a default
model. This command validates and displays the selection without contacting
Ollama, submitting a prompt, or loading the model.

Submit one prompt to the configured model and print validated answer chunks as
they arrive. In
PowerShell, `--%` preserves the nested quotes through the Gradle batch wrapper:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

The request goes only to the fixed loopback endpoint
`http://127.0.0.1:11434/api/chat`, sends the current prompt as the final user
message, requests streaming NDJSON, validates each answer chunk before printing
it, and assembles the same chunks into one bounded final answer. This one-shot
command supplies empty history; the conversation command described below sends
the selected ordered history before each prompt. The serialized request is limited to 1 MiB. Ordinary
requests explicitly disable thinking and use a 512-token
generation default. The prompt is limited to 4,096 characters; the response is
limited to 1 MiB and 65,536 characters; and the complete request is bounded to
five minutes with a 60-second no-data deadline. Ordinary thinking-off requests
show only answer content. Explicit thinking-on requests show `Thinking...`, then
`Answer:`, without displaying raw reasoning. Ctrl+C interrupts the command and
cancels the response subscription. Provider truncation, local byte/text limits,
pre-response unavailability, request rejection, accepted-stream transport loss,
total timeout, inactivity timeout, cancellation, malformed records, and
incomplete streams are distinct outcomes with safe recovery guidance. If one
occurs after visible content, KAOS terminates the
stdout line and identifies the output as partial in safe stderr guidance. KAOS
does not retry after partial output and does not echo prompts
or raw Ollama failures in errors. However,
the quoted prompt can remain in shell history or be visible as a process
argument, so this developer CLI is not an appropriate input surface for
secrets or other private prompts.

Start one foreground session that actually retains and uses earlier clean turns:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
./gradlew.bat run --args=conversation
```

On the first run, conversation `1` is created and selected automatically. Later
runs restore the newest bounded working set from local SQLite and select its
newest conversation. Type prompts normally; use `/new` to create and select
another conversation, `/select <id>` to switch, `/list` to inspect the loaded
identifiers, `/help` for controls, and `/exit` to finish. Only clean
user/assistant pairs are stored; failed or partial AI turns are not. Conversations
remain isolated and clean turns survive process exit. One foreground working set
allows 8 conversations; each conversation allows 32 clean turns; every stored
message allows 65,536 Unicode code points. KAOS rejects a ninth loaded
conversation or thirty-third turn without contacting Ollama. These safety bounds
are not configurable. The durable collection can exceed 8 conversations, but
older conversations outside the newest loaded set are not yet pageable from the
CLI. Exact last-selection persistence, deletion, trimming, summarization, and
local provider-token estimation remain deferred.

By default the database is `%USERPROFILE%\.kaos\conversations.db` on Windows
(the Java user-home directory plus `.kaos/conversations.db`). Override only the
data directory with `KAOS_CONVERSATION_DATA_DIRECTORY`; direct JVM launches may
instead use `-Dkaos.conversation.data-directory=<directory>`, which takes
precedence. KAOS fixes the filename, creates the directory when needed, validates
schema version 1 on every conversation startup, and reports storage failures as
`KAOS-CONVERSATION-002` without exposing paths, SQL, or conversation content.
Locked, corrupt, read-only, capacity, unavailable, invalid-state, and unknown
failures receive distinct recovery guidance. KAOS does not retry, repair,
replace, or delete the database automatically. A write failure after an answer
was displayed explicitly says that the completed turn was not saved.
See [conversation creation and selection](docs/evolution/conversation-creation-selection.md)
for the lifecycle and [conversation limits and validation](docs/evolution/conversation-limits-validation.md)
for the exact bounds and recovery behavior. See
[conversation restore](docs/evolution/conversation-restore.md) for the persistent
startup and write flow, and [persistence failure handling](docs/evolution/persistence-failure-handling.md)
for safe operator recovery.

The model remains your explicit choice. Current measurements recommend
`qwen3:1.7b` only for a fast connectivity smoke test,
`qwen3:4b-instruct` for ordinary local development, and `qwen3:4b` only for
opt-in reasoning where extra latency and token use are acceptable. These are
developer profiles, not hard-coded defaults or automatic fallbacks. See the
[model scenario benchmark](docs/evolution/ollama-model-scenario-benchmark.md)
for the controlled process, results, and limitations.

Enable reasoning only by deliberately selecting both a supported reasoning
model and thinking mode:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
$env:KAOS_OLLAMA_THINKING = "on"
./gradlew.bat --% run --args="ollama-prompt \"<reasoning prompt>\""
```

KAOS sends `think: true`. When the provider emits separated thinking, KAOS
prints one `Thinking...` progress line and then transitions to an `Answer:`
section while keeping the raw reasoning trace hidden. Unsupported models fail
safely and are not replaced or retried automatically. See the
[thinking policy and benchmark](docs/evolution/ollama-thinking-policy-and-benchmark.md).
Thinking-on requests default to 2,048 generated tokens. Set
`KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT` to a deliberate value from 64 through 4,096
when a request needs a different bounded maximum. See the
[response-generation limit benchmark](docs/evolution/ollama-response-generation-limit-benchmark.md).

Handled startup or application failures return exit code `1` and emit one safe
record such as `ERROR [KAOS-CONFIG-001] ...` on standard error. Expected CLI
usage errors retain exit code `2`. Exception messages, stack traces, arguments,
and configured values are not logged.

The application name can be overridden locally. For the Gradle run workflow,
set the environment variable:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run
```

Direct JVM launches may instead set `-Dkaos.app.name="Local KAOS"`; that system
property takes precedence over `KAOS_APP_NAME`.

The application-name default is `KAOS`. Names are trimmed, limited to 64
Unicode characters, and may contain letters, numbers, spaces, periods,
underscores, or hyphens.

The Ollama model uses `kaos.ollama.model` before `KAOS_OLLAMA_MODEL` and has no
default. Its context uses `kaos.ollama.context-window` before
`KAOS_OLLAMA_CONTEXT_WINDOW`, then the measured 4,096-token ordinary default;
accepted context values are 2,048 through 65,536. A model name is trimmed,
limited to 128 ASCII characters, and supports ordinary or namespaced Ollama
identifiers with an optional tag. Thinking uses `kaos.ollama.thinking` before
`KAOS_OLLAMA_THINKING`, then defaults to `off`; only `off` and `on` are
accepted. No secret, remote endpoint, prompt-file, automatic thinking mode, or
persistent configuration is implemented. Response generation uses
`kaos.ollama.response-token-limit` before
`KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`, then 512 for thinking off or 2,048 for
thinking on; only whole values from 64 through 4,096 are accepted.

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

The living architecture page is part of the feature definition of done whenever
a feature changes the system structure or its verified architectural status.

## Verify the local application

Run the complete from-clean-state checkpoint:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

This compiles, runs all tests—including the application-to-Ollama-to-SQLite
restart integration suite—packages the application, executes deterministic `status` and
`help` smoke commands, and prints a final success checkpoint only when every
prerequisite passes. The integration suite does not require a running Ollama
installation or external network. Use `verifyLocal` without `clean` for an
incremental check.

## Next checkpoint

Epics 000-005 and all ten Epic 005 features are complete. The installed
distribution ingested a synthetic document and returned a grounded cited answer
through local Ollama with exit code 0. See the [Epic 005 exit
evidence](docs/evolution/epic-005-exit.md). Epic #10 is next and remains inactive.
