# KAOS

KAOS is one evolving Java application, delivering one useful goal at a time.
The development-model reset is complete; capabilities now grow incrementally
inside the verified single application.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Completed epics: [Epic 000 — Development Model Reset](https://github.com/karanbabu2110/KAOS/issues/815), [Epic 001 — Minimal KAOS Application](https://github.com/karanbabu2110/KAOS/issues/2), [Epic 002 — First AI Integration](https://github.com/karanbabu2110/KAOS/issues/3), [Epic 003 — Conversation Capability](https://github.com/karanbabu2110/KAOS/issues/9), [Epic 004 — Local Persistence](https://github.com/karanbabu2110/KAOS/issues/823), [Epic 005 — First Knowledge and RAG Capability](https://github.com/karanbabu2110/KAOS/issues/11), [Epic 006 — First Memory Capability](https://github.com/karanbabu2110/KAOS/issues/10), and [Epic 007 — First Tool Integration](https://github.com/karanbabu2110/KAOS/issues/15)
- Active epic: [Epic 008 — Multi-Tool Capability](https://github.com/karanbabu2110/KAOS/issues/63); HTTP retrieval, SearXNG web search, the shared registry/selection/permission runtime, and bounded content-free [tool execution history](docs/evolution/tool-execution-history.md) are implemented; [the contract refinement](docs/evolution/tool-contract-refinement.md) retains that proven boundary without adding a framework
- Release checkpoint: KAOS 1.6.0 is being prepared for the completed HTTP GET capability; the latest published release remains [KAOS 1.5.0](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/releases/tag/v1.5.0)
- Repository state: one root Gradle/Java 21 application with one production entry point, explicit bounded memory, local SQLite conversations/knowledge/memory/tool history, bounded local Ollama chat and embeddings, grounded answers with citations, three concrete approval-gated tools, SearXNG-backed discovery, and a read-only `tools` catalog with privacy-safe configuration status
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

The current cumulative release checkpoint is **1.6.0**, prepared for annotated
tag and GitHub Release `v1.6.0` from verified merged `main`.

This release represents all verified work through Feature 008.01, adding the
foreground `http_get` path with exact-host configuration, exact-URL approval,
public-destination validation, one bounded strict UTF-8 GET attempt, no-tool
model continuation, content-free audit output, and deterministic integration
evidence. It is a capability milestone, not a production-readiness,
unrestricted-network, web-search, browser, or permanent API-compatibility
claim. See the [1.6.0 release notes](docs/releases/v1.6.0.md).

## Run the application

### Environment variables

All KAOS-specific environment variables currently read by the application are
listed here. A matching JVM system property, where supported, takes precedence
over its environment variable.

| Environment variable | Accepted value and default | Used by |
| --- | --- | --- |
| `KAOS_APP_NAME` | Display name of at most 64 safe characters; defaults to `KAOS` | All commands and status output |
| `KAOS_OLLAMA_MODEL` | Explicit installed Ollama chat model name; no default | `ollama-model`, `ollama-prompt`, `conversation`, `knowledge-ask`, `read-local-file`, `http-get`, `web-search` |
| `KAOS_OLLAMA_CONTEXT_WINDOW` | Whole number from 2,048 through 65,536; defaults to `4096` | Commands using `KAOS_OLLAMA_MODEL` |
| `KAOS_OLLAMA_THINKING` | `off` or `on`; defaults to `off` | Commands using `KAOS_OLLAMA_MODEL` |
| `KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT` | Whole number from 64 through 4,096; defaults to `512` with thinking off or `2048` with thinking on | Commands using `KAOS_OLLAMA_MODEL` |
| `KAOS_OLLAMA_EMBEDDING_MODEL` | Explicit installed Ollama embedding model name; no default | `knowledge-ingest`, `knowledge-retrieve`, `knowledge-ask` |
| `KAOS_KNOWLEDGE_DATA_DIRECTORY` | Directory containing `knowledge.db`; defaults to the current user's `.kaos` directory | Knowledge commands |
| `KAOS_CONVERSATION_DATA_DIRECTORY` | Directory containing `conversations.db`; defaults to the current user's `.kaos` directory | `conversation` |
| `KAOS_MEMORY_DATA_DIRECTORY` | Directory containing `memory.db`; defaults to the current user's `.kaos` directory | Memory commands and `ollama-prompt` memory lookup |
| `KAOS_TOOL_HISTORY_DATA_DIRECTORY` | Directory containing `tool-history.db`; defaults to the current user's `.kaos` directory | Tool execution recording and `tool-history` |
| `KAOS_TOOL_READ_ROOT` | Required absolute, non-filesystem-root directory; no default | `tools` status; file selection in `read-local-file`, `web-search`, or `conversation` |
| `KAOS_WEB_SEARCH_SEARXNG_URL` | Optional trusted HTTP(S) service origin, e.g. `http://127.0.0.1:8080`; no default. JVM override: `kaos.web-search.searxng-url` | `tools` status; search selection in `web-search` or `conversation` |
| `KAOS_HTTP_ALLOWED_HOSTS` | Required comma-separated exact host names; no default | `tools` status; `http-get`, before showing an approval request |

### Commands and arguments

The quoted placeholders below represent one process argument. In PowerShell,
use `--%` with the Gradle batch wrapper when the `--args` value contains nested
quotes.

| Command shape | Arguments | Outcome |
| --- | --- | --- |
| `kaos` or `kaos status` | None | Show local application status |
| `kaos help` or `kaos --help` | None | Show the exact supported command syntax |
| `kaos tools` | None | List fixed tools and privacy-safe local configuration status without executing them |
| `kaos tool-history` | None | List up to 20 recent content-free terminal tool attempts, newest first |
| `kaos memory-create answer-detail <value>` | `<value>` is `concise`, `balanced`, or `detailed` | Create the fixed answer-detail memory without overwriting it |
| `kaos memory-inspect answer-detail` | Fixed key `answer-detail` | Show whether the memory exists and its value |
| `kaos memory-edit answer-detail <value>` | `<value>` is `concise`, `balanced`, or `detailed` | Replace an existing answer-detail value |
| `kaos memory-delete answer-detail` | Fixed key `answer-detail` | Delete the existing answer-detail memory |
| `kaos memory-privacy` | None | Show content-free memory policy and state |
| `kaos knowledge-ingest "<path>"` | One local `.txt` file path | Extract, chunk, embed, and store one bounded document |
| `kaos knowledge-retrieve "<query>"` | One quoted knowledge query | Rank stored chunks and construct grounded context |
| `kaos knowledge-ask "<question>"` | One quoted knowledge question | Generate one grounded answer with source citations |
| `kaos read-local-file "<question>"` | One quoted question that identifies a relative file for the model | Ask about one model-requested, validated, explicitly approved file |
| `kaos http-get "<question>"` | One quoted question from which the model may select an allowed HTTPS URL | Ask about one model-requested, explicitly approved web resource |
| `kaos web-search "<question>"` | One quoted question; model chooses search, local file, or no tool | At most one approved tool, followed by one no-tools answer |
| `kaos ollama-status` | None | Check the fixed local Ollama endpoint |
| `kaos ollama-model` | None | Validate and display the configured chat model |
| `kaos ollama-prompt "<prompt>"` | One quoted prompt | Generate one local Ollama answer |
| `kaos conversation` | None; subsequent input is interactive | Start or resume a persistent local conversation |

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

Ask the local model about one file below an explicitly allowed directory:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_TOOL_READ_ROOT = (Resolve-Path ".").Path
./gradlew.bat --% run --args="read-local-file \"Explain src/main/java/io/kaos/app/KaosApplication.java\""
```

If the model requests that relative file, KAOS validates its metadata and shows
the exact resolved path and byte count. Type `approve` to authorize that one
read attempt, or `deny` to finish without reading. After approval, KAOS reads at
most 2,048 strict UTF-8 bytes, supplies a structured tool result only to fixed
loopback Ollama for the current answer, and prints a content-free audit outcome.
The result request advertises no tools, so the model cannot chain another tool
call. Supported extensions are `.txt`, `.md`, `.log`, `.java`, `.kt`, `.kts`,
`.gradle`, `.json`, `.xml`, `.yaml`, `.yml`, `.properties`, and `.csv`; compound
or binary formats such as `.doc`, `.docx`, and `.pdf` remain unsupported. See
[tool integration testing](docs/evolution/tool-integration-testing.md).

Ask the local model about one resource on an explicitly allowed HTTPS host:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3.5:4b"
$env:KAOS_HTTP_ALLOWED_HOSTS = "example.com,docs.oracle.com"
./gradlew.bat --% run --args="http-get \"Use http_get to fetch and summarize https://example.com/\""
```

The model may answer directly or request exactly one `http_get` URL. KAOS
accepts only HTTPS on the standard port, requires an exact configured host,
shows the normalized URL, and requires `approve` before DNS resolution or any
GET. The approved foreground request follows no redirects, sends no credentials
or cookies, retries nothing, and accepts at most 32,768 strict UTF-8 bytes of
text, JSON, or XML. Its result is untrusted model context for one final local
Ollama answer; no further tool is advertised. DNS is checked for non-public
addresses immediately before execution, but a host changing addresses between
that check and the HTTP client's connection remains a documented residual DNS
rebinding limitation. See [HTTP GET tool](docs/evolution/http-get-tool.md).

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

Start Docker Desktop, then start the repository-provided separate SearXNG
container from the KAOS root:

```powershell
docker compose up -d
docker compose ps
```

Setting the URL does not start the service. Follow the numbered setup guide
linked below, including its JSON readiness check. Use `--console=plain` for
interactive runs so Gradle progress bars do not overwrite approval prompts.

Then configure KAOS and run a search:

```powershell
$env:KAOS_WEB_SEARCH_SEARXNG_URL = "http://127.0.0.1:8080"
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
./gradlew.bat --% --console=plain run --args="web-search \"What is the latest stable Spring Boot version?\""
```

The model can propose `web_search`, `read_local_file`, or answer directly, both
here and in `conversation`. Search requires approval of the exact query.
Although KAOS contacts configured SearXNG, that service may send the query to
external search engines. Search returns at most five bounded titles, URLs, and
snippets; URLs are data only and are never fetched. Results are untrusted data,
not instructions or execution authority. There is no tool chaining or automatic
retry. Missing SearXNG configuration does not prevent startup or other tools.
See [separate SearXNG setup](docs/development/searxng-setup.md) and the
[search contract and limits](docs/evolution/web-search-tool.md).

Start one foreground session that actually retains and uses earlier clean turns:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
./gradlew.bat --console=plain run --args=conversation
```

On the first run, conversation `1` is created and selected automatically. Later
runs restore the newest bounded working set from local SQLite and select its
newest conversation. Type prompts normally; use `/new` to create and select
another conversation, `/select <id>` to switch, `/list` to inspect the loaded
identifiers, `/help` for controls, and `/exit` to finish. Only clean
user/assistant pairs from no-tool answers are stored; failed, partial, or tool-backed
turns are not. Tool selection buffers output until a complete valid response;
tool-backed turns are excluded from both SQLite and subsequent in-memory history.
Conversations
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

Feature 008.08 records the evidence-backed
[tool contract refinement](docs/evolution/tool-contract-refinement.md). The
three concrete tools and execution-history consumer support the current small
shared boundary; no further runtime abstraction is introduced. Epic 009 begins
with [009.01 - Bounded Agent Use Case](https://github.com/karanbabu2110/KAOS/issues/900)
after this final Epic 008 checkpoint merges.
