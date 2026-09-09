# KAOS Developer Guide

This is the practical entry point for developing the current KAOS application.
It describes the application that exists now; planned capabilities do not add
setup or operational requirements until they are implemented.

## Prerequisites

- Git
- A Java 21 JDK available to the Gradle toolchain
- PowerShell or Command Prompt on Windows, or a POSIX-compatible shell on
  Linux/macOS
- Network access the first time Gradle needs to download declared build or test
  dependencies
- Optional: a local Ollama server on `127.0.0.1:11434` to demonstrate
  `ollama-status`, plus one installed model to demonstrate `ollama-prompt` and
  `conversation`, `knowledge-ask`, and `read-local-file`; neither is required
  to build or run automated tests

No system Gradle installation is required. Use the Gradle wrapper committed to
the repository.

Check the active Java runtime:

```powershell
java -version
```

## Get the repository

```powershell
git clone https://github.com/Knowledge-Autonomous-Operating-System/KAOS.git
cd KAOS
```

All commands in this guide run from the repository root.

## Verify a new checkout

Windows PowerShell or Command Prompt:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Linux or macOS:

```bash
./gradlew clean verifyLocal --no-daemon --warning-mode=all
```

This canonical checkpoint compiles production and test source, runs all tests
and checks, creates the application artifacts, runs deterministic `status` and
`help` smoke checks, and prints the final success message only when every step
passes.

Use the faster incremental form after a previously verified clean checkout:

```powershell
./gradlew.bat verifyLocal --no-daemon
```

## Run the application

Show the current local status:

```powershell
./gradlew.bat run --args=status
```

Show supported commands:

```powershell
./gradlew.bat run --args=help
```

Persist the first bounded memory locally:

```powershell
./gradlew.bat --% run --args="memory-create answer-detail balanced"
```

Inspect whether that fixed memory exists and, when present, its structured
value:

```powershell
./gradlew.bat --% run --args="memory-inspect answer-detail"
```

Replace or remove an existing preference explicitly:

```powershell
./gradlew.bat --% run --args="memory-edit answer-detail detailed"
./gradlew.bat --% run --args="memory-delete answer-detail"
```

Review the content-free memory privacy boundary and present/absent state:

```powershell
./gradlew.bat run --args=memory-privacy
```

The fixed key accepts only `concise`, `balanced`, or `detailed`. Creation is
explicit, persists in `memory.db`, and does not overwrite an existing value—even
from a later application process. Set `KAOS_MEMORY_DATA_DIRECTORY` to select
the parent directory; the `kaos.memory.data-directory` system property takes
precedence, and the default is the local user's `.kaos` directory. One-shot
`ollama-prompt` requests read this value and apply its fixed instruction;
inspection reports only `concise`, `balanced`, `detailed`, or absence. Editing
requires a present value and deletion requires a present row; both changes are
atomic and survive later application processes. See
[explicit memory creation](../evolution/explicit-memory-creation.md) and
[memory storage](../evolution/memory-storage.md), then
[memory use in AI context](../evolution/memory-ai-context.md) and
[memory inspection](../evolution/memory-inspection.md), then
[memory editing and deletion](../evolution/memory-editing-deletion.md), then
[memory privacy controls](../evolution/memory-privacy-controls.md), then
[memory testing and evaluation](../evolution/memory-testing-evaluation.md).

Admit one local UTF-8 `.txt` file, up to 1 MiB, for later knowledge processing:

```powershell
$env:KAOS_OLLAMA_EMBEDDING_MODEL = "embeddinggemma"
$env:KAOS_KNOWLEDGE_DATA_DIRECTORY = "D:\kaos-data"
./gradlew.bat --% run --args="knowledge-ingest \"D:\documents\notes.txt\""
```

The command requires one readable, non-empty regular file and rejects symbolic
links, invalid UTF-8, other extensions, and oversized input. It extracts the
admitted bytes exactly without whitespace or line-ending normalization, then
splits the text into immutable chunks of at most 1,000 Unicode code points with
200 code points of overlap. Success prints the safe file name,
`text/plain; charset=utf-8`, byte count, Unicode code-point count, and chunk
count, embedding count, and dimension count without printing text or vector
values. The selected model must already be installed. KAOS sends exact chunks
only to fixed loopback Ollama `/api/embed`, disables provider truncation, and
atomically stores exact chunks and vectors in fixed `knowledge.db`. The optional
data-directory setting selects only its parent directory; the system property
`kaos.knowledge.data-directory` takes precedence. The path may remain in shell
history or process arguments, but KAOS does not print the path or content.
See [single document ingestion](../evolution/single-document-ingestion.md) and
[text extraction](../evolution/text-extraction.md), then
[document chunking](../evolution/document-chunking.md) and
[embedding generation](../evolution/embedding-generation.md), then
[vector storage](../evolution/vector-storage.md).

Retrieve bounded context references from the same local knowledge database:

```powershell
./gradlew.bat --% run --args="knowledge-retrieve \"What does the document say about caching?\""
```

The command uses the same `KAOS_OLLAMA_EMBEDDING_MODEL` and
`KAOS_KNOWLEDGE_DATA_DIRECTORY` settings as ingestion. It prints at most three
safe references and scores, followed by the bounded prompt size, included
context count, and stable content-free citation coordinates—not the query,
chunk content, model name, vectors, or prompt.
See [relevant-context retrieval](../evolution/relevant-context-retrieval.md) and
[grounded prompt construction](../evolution/grounded-prompt-construction.md),
then [source attribution](../evolution/source-attribution.md).
For deterministic end-to-end evidence that requires no Ollama process, run the
[RAG evaluation test](../evolution/rag-evaluation-testing.md).

Generate one grounded answer through the configured local chat and embedding
models:

```powershell
./gradlew.bat --% run --args="knowledge-ask \"When do backups run?\""
```

The command streams the answer and prints the content-free citation manifest
only after clean provider completion. It uses the existing
`KAOS_OLLAMA_MODEL`, `KAOS_OLLAMA_EMBEDDING_MODEL`, and
`KAOS_KNOWLEDGE_DATA_DIRECTORY` settings. See
[grounded answer generation](../evolution/grounded-answer-generation.md).

Ask the configured local model about one explicitly approved local file:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_TOOL_READ_ROOT = (Resolve-Path ".").Path
./gradlew.bat --% run --args="read-local-file \"Explain src/main/java/io/kaos/app/KaosApplication.java\""
```

The question is not file-read permission. The model must first request exactly
one supported relative path below `KAOS_TOOL_READ_ROOT` (or the higher-priority
`kaos.tool.read-root` system property). KAOS then prints the exact resolved path
and size. Enter exactly `approve` to authorize one read attempt or `deny` to
finish without reading:

```text
Tool: read_local_file
Exact file: <resolved path>
Size: <bytes> bytes
If approved, this file's text will be supplied to the configured local Ollama model for the current answer only.
Type 'approve' to authorize one read attempt or 'deny' to cancel tool use.
```

An approved read is revalidated, limited to 2,048 bytes, decoded as strict
UTF-8, and sent as a structured `tool` message to fixed loopback Ollama. The
continuation request advertises no tools, preventing tool chaining. KAOS prints
the final answer and one content-free audit line; it does not print the file
content as a diagnostic or persist it. Denial, invalid approval input,
end-of-input, and pre-read cancellation execute no read. Supported file types
are `.txt`, `.md`, `.log`, `.java`, `.kt`, `.kts`, `.gradle`, `.json`, `.xml`,
`.yaml`, `.yml`, `.properties`, and `.csv`. `.doc`, `.docx`, `.pdf`, other
binary/compound formats, multiple files, directories, retries, and remote
providers are not supported. See
[tool integration testing](../evolution/tool-integration-testing.md).

Ask about one explicitly approved HTTPS resource:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_HTTP_ALLOWED_HOSTS = "example.com,docs.oracle.com"
./gradlew.bat --% run --args="http-get \"Summarize https://example.com/reference\""
```

The higher-priority `kaos.tool.http.allowed-hosts` system property or
`KAOS_HTTP_ALLOWED_HOSTS` environment variable must contain comma-separated
exact host names. Wildcards, ports, paths, and empty entries are invalid. The
command validates one model-requested standard-port HTTPS URL, displays it to
the user, and resolves DNS only after exact `approve`. Execution rejects any
non-public resolved address, follows no redirects, sends no credentials or
cookies, and returns only a successful supported strict UTF-8 body of at most
32,768 bytes. Entering `deny`, invalid input, cancellation, or end-of-input
performs no DNS or HTTP request. Tests use injected deterministic boundaries
and never contact the public internet. See
[HTTP GET tool](../evolution/http-get-tool.md).

The no-argument form is equivalent to `status`:

```powershell
./gradlew.bat run
```

The successful status output is:

```text
KAOS application baseline is running.
```

Check the fixed local Ollama endpoint:

```powershell
./gradlew.bat run --args=ollama-status
```

When Ollama is available, the command prints a validated version such as:

```text
Local Ollama is reachable (version 0.32.1).
```

This command performs one bounded `GET /api/version` request to
`http://127.0.0.1:11434`. It does not select a model, submit a prompt, stream a
response, read credentials, or support a remote endpoint. `status` and `help`
do not create the Ollama client or make a network request.

Configure and inspect the model reserved for later AI commands:

Install the ordinary development recommendation explicitly if it is not
already present:

```powershell
ollama pull qwen3:4b-instruct
```

KAOS never runs this installation command for you.

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat run --args=ollama-model
```

Successful output is:

```text
Configured local Ollama model: qwen3:4b-instruct (context window: 4096 tokens, thinking: off, response limit: 512 tokens).
```

`ollama-model` validates and displays local process configuration only. It does
not contact Ollama, check whether the model is installed, download or load a
model, submit a prompt, or produce a response.

Submit one prompt and wait for one complete response. PowerShell needs its
stop-parsing token so nested quotes survive the Gradle batch wrapper:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

On Linux or macOS:

```bash
export KAOS_OLLAMA_MODEL=qwen3:4b-instruct
export KAOS_OLLAMA_THINKING=off
export KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT=512
./gradlew run --args='ollama-prompt "Why is the sky blue?"'
```

The command validates a single 4,096-character prompt, retrieves the optional
`answer-detail` memory, loads the explicit model selection, and sends
`POST /api/chat` to the fixed loopback Ollama endpoint with `stream` set to
`true`. A present value adds one bounded KAOS-controlled `system` message before
the final `user` message: concise requests essentials only, balanced requests
brief but sufficient explanation, and detailed requests relevant context and
explanation. When memory is absent, the serialized message sequence remains the
same single user message as before. Retrieval failure stops before model loading
or provider submission. Conversation and `knowledge-ask` requests do not apply
this application-wide preference. The client also accepts an explicitly
supplied ordered history before the final user message. It parses newline-delimited JSON as it arrives, validates
each answer chunk, prints and flushes it once, and assembles the same chunks into
the bounded final answer. It also sends `think: false` and
`options.num_predict: 512` for this ordinary configuration. The serialized
request and response body are each bounded to 1 MiB; generated answer and hidden
thinking text to 65,536 Unicode
code points each, inactivity to 60 seconds, and the complete request to five
minutes. The HTTP publisher supplies one bounded item at a time. Ordinary
thinking-off requests retain the unlabeled answer stream. Explicit thinking-on
requests show a content-free progress line and an answer heading without
displaying raw reasoning. The `read-local-file` command uses a separate client
operation that advertises only the fixed `read_local_file` definition and
returns either an ordinary answer or one validated pending tool request. After
an approved bounded read, its continuation contains the assistant tool call and
structured tool result but advertises no tools. There is no retry after visible output, arbitrary
user-supplied system prompt, executable tool, image, remote-provider, or AI
response persistence behavior.

The separate `http-get` operation advertises only `http_get`. It accepts one
strict URL request or an ordinary answer. After approved execution, its
continuation contains the exact assistant call and structured bounded result
while advertising no tools. It does not automatically select, chain, search,
open another URL, or persist response content.

The local-file tool validator requires one explicit read root from
`kaos.tool.read-root` or `KAOS_TOOL_READ_ROOT`, with the system property taking
precedence. There is no default, and a filesystem root is rejected as too
broad. The configured value must be absolute so its authority does not depend
on the process working directory. Only `read-local-file` loads this setting,
and only after the local model has requested one structurally valid path.

After validation, `ReadLocalFileApprovalRequest` can render the exact local
target, byte count, current-answer Ollama disclosure, and the two explicit
tokens `approve` and `deny`. Only the exact lowercase `approve` token creates
one grant, and that grant can supply its target for one execution attempt.
Denial, explicit cancellation, end of input, or any other response creates no
grant. `ReadLocalFileExecutor` can consume an approved grant once, revalidate
the target around a no-follow read, strictly decode at most 2,048 UTF-8 bytes,
and return one complete result. The application wires these APIs only through
`read-local-file` and sends a successful result to Ollama for one no-tools
continuation.

`ReadLocalFileAuditContext` can assign the validated target a random
per-invocation UUID and produce one final content-free record of the approval
decision and broad outcome. The identity is not derived from the path, and the
record contains no path, content, timestamp, or failure detail. There is no
audit sink or persistence; application integration will decide where the
ephemeral record is observed.

Known local-file tool failures map to fixed `KAOS-TOOL-READ-001` through
`KAOS-TOOL-READ-012` diagnostics with content-free recovery guidance. The
mapper accepts only typed permission and execution exceptions plus explicit
invalid-request and inactive-state cases. Denial and cancellation before
approval remain normal non-execution outcomes. Nothing prints these diagnostics
until an application tool coordinator is implemented.

Clean `done_reason: stop` completion returns exit `0`. Provider
`done_reason: length`, local byte/text ceilings, inactivity or total timeout,
thread interruption, malformed/incomplete streams, and transport failure return
exit `1` through distinct internal outcomes. When content was already visible,
KAOS finishes the stdout line before printing a coded stderr error beginning
`Partial streaming output was displayed before clean completion.` It never
includes the prompt, raw response record, reasoning, or exception detail.

The CLI argument can remain in shell history and may be visible to local
process inspection. Do not use this developer command for secrets or other
private prompts. KAOS does not echo the prompt, raw provider body, configured
model, exception, or stack trace when the request fails.

An unknown command or extra argument returns usage exit code `2`. A handled
configuration or application failure returns exit code `1` and a safe coded
error on standard error. Supplied values, exception messages, and stack traces
are not logged.

Start selectable persistent local conversations:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
./gradlew.bat run --args=conversation
```

The Gradle `run` task forwards standard input. On an empty database KAOS creates
and selects conversation `1`; on later runs it restores the newest bounded
working set and selects its newest conversation. Type any nonblank prompt to send
it with that conversation's earlier clean turns. The available controls are
`/new`, `/select <id>`, `/list`, `/help`, and `/exit`.

Each successful request appends one validated user message and one assistant
message. Failed or partial requests are not retained, although the session can
continue; its final exit remains nonzero if an AI request failed. Separate
conversation identifiers keep their histories isolated. New identifiers and
complete clean turns are stored before the foreground session accepts them. One
loaded working set is limited to 8 conversations, each conversation to 32 clean turns, each immutable
history to 64 messages, and each stored message to 65,536 Unicode code points.
The existing current-prompt limit remains 4,096 code points.

KAOS rejects a ninth `/new` without changing the active identifier. It rejects
a prompt that would become the thirty-third turn before model configuration is
loaded or Ollama is contacted. Use `/select` to choose a loaded conversation
with capacity. These initial limits are not configurable. The durable collection
can exceed 8 conversations, but the CLI restores only the newest 8 and does not
yet page older entries. Exact last selection, naming, deletion, trimming,
summarization, automatic rollover, and provider-token estimation are not stored.
See [conversation restore](../evolution/conversation-restore.md),
[persistence failure handling](../evolution/persistence-failure-handling.md), and
[conversation limits and validation](../evolution/conversation-limits-validation.md).

The default database is `.kaos/conversations.db` below the Java user-home
directory. Set `KAOS_CONVERSATION_DATA_DIRECTORY` to choose another local data
directory, or use the higher-precedence JVM property
`kaos.conversation.data-directory`. Both configure a directory; KAOS always uses
the fixed `conversations.db` filename. Startup creates the directory if needed,
initializes or validates schema version 1, and returns
`KAOS-CONVERSATION-002` without private database detail if storage cannot be used.
The message distinguishes locked, corrupt, read-only, capacity, unavailable,
invalid-state, and unknown failures and says whether startup, `/new`, or a
completed-turn write failed. KAOS never retries, repairs, replaces, or deletes
the database automatically.

## Local configuration

Override the display name for an ordinary local run:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run --args=status
```

Direct JVM launches may use the `kaos.app.name` system property. Configuration
precedence is:

1. `-Dkaos.app.name=...`
2. `KAOS_APP_NAME`
3. the safe default `KAOS`

Names are trimmed, limited to 64 Unicode characters, and may contain letters,
numbers, spaces, periods, underscores, or hyphens.

The local Ollama model and its bounded context are configured separately:

1. `-Dkaos.ollama.model=...` for a direct JVM launch
2. `KAOS_OLLAMA_MODEL`
3. no default; an explicit selection is required by `ollama-model` and
   `ollama-prompt`

The context-window precedence is:

1. `-Dkaos.ollama.context-window=...` for a direct JVM launch
2. `KAOS_OLLAMA_CONTEXT_WINDOW`
3. 4,096 tokens

KAOS accepts whole values from 2,048 through 65,536 and sends the selection as
Ollama `options.num_ctx`. Use 2,048 only for deliberately short smoke tests.
The [controlled context benchmark](../evolution/ollama-context-window-benchmark.md)
shows why 4,096 is the ordinary default and why larger values remain opt-in.

The thinking-mode precedence is:

1. `-Dkaos.ollama.thinking=off|on` for a direct JVM launch
2. `KAOS_OLLAMA_THINKING=off|on`
3. `off`

Ordinary requests should use `qwen3:4b-instruct` with `off`. To opt into
reasoning deliberately:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
$env:KAOS_OLLAMA_THINKING = "on"
./gradlew.bat run --args=ollama-model
./gradlew.bat --% run --args="ollama-prompt \"<reasoning prompt>\""
```

KAOS sends the boolean mode explicitly. When thinking is on and the provider
emits thinking records, KAOS prints `Thinking...` once, then prints `Answer:`
before streaming final-answer chunks. Raw reasoning remains separately bounded
and is never printed, logged, or included in errors. If the provider emits no
thinking, KAOS does not claim that thinking occurred and begins with `Answer:`.
It does not infer a mode from the prompt, retry with thinking off, or substitute
a model. An unsupported model therefore fails through the safe prompt-rejection
path.

The response-token-limit precedence is:

1. `-Dkaos.ollama.response-token-limit=...` for a direct JVM launch
2. `KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`
3. 512 tokens when thinking is `off`, or 2,048 when thinking is `on`

KAOS accepts whole values from 64 through 4,096 and sends the selection as
Ollama `options.num_predict`. For example, request more bounded space for a
larger ordinary answer:

```powershell
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "1024"
./gradlew.bat --% run --args="ollama-prompt \"<larger prompt>\""
```

KAOS does not use Ollama's unbounded default, retry automatically, or increase
the limit after truncation. The
[response-generation limit benchmark](../evolution/ollama-response-generation-limit-benchmark.md)
records the comparison and explains the separate defaults.

PowerShell environment values persist for the current terminal session. After
running a low-limit boundary test, inspect the effective KAOS configuration
before treating `KAOS-AI-003` as evidence that a default is too small:

```powershell
Get-ChildItem Env:KAOS_OLLAMA_*
./gradlew.bat run --args=ollama-model
```

Return specifically to the mode-based response default by removing only the
response-limit override, then inspect the configuration again:

```powershell
Remove-Item Env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT -ErrorAction SilentlyContinue
./gradlew.bat run --args=ollama-model
```

This does not remove the selected model, context window, or thinking mode.
When thinking is `on`, both hidden reasoning and the final answer consume the
same generated-token allowance, so ordinary questions should normally use the
instruct model with thinking `off`.

Model names are trimmed, limited to 128 ASCII characters, and accept ordinary
or slash-separated identifiers containing letters, numbers, periods,
underscores, or hyphens, followed by an optional colon tag. Examples include
`llama3.2:latest` and `hf.co/team/model-name:Q4_K_M`.

Use the measured profile that matches the current goal:

| Goal | Explicit model | Guidance |
| --- | --- | --- |
| Connectivity smoke test | `qwen3:1.7b` | Fastest and smallest; do not treat its answer as the quality baseline |
| Ordinary local development | `qwen3:4b-instruct` | Current recommendation for answers, summaries, and simple Java help |
| Explicit reasoning experiment | `qwen3:4b` | Opt-in only; the measured reasoning probe needed 674 generated tokens and about 18 seconds |

These names are recommendations, not KAOS defaults. Select and install models
deliberately; a missing model is not downloaded or replaced automatically. The
[model scenario benchmark](../evolution/ollama-model-scenario-benchmark.md)
records the prompts, controls, quality observations, performance, hardware,
licenses, and decision limits. Thinking behavior is recorded in the
[thinking policy and benchmark](../evolution/ollama-thinking-policy-and-benchmark.md).
Response-generation limits are recorded in the
[response-generation limit benchmark](../evolution/ollama-response-generation-limit-benchmark.md).

Do not commit credentials or other secrets. The Ollama endpoint remains fixed
to loopback. No secret, remote endpoint, prompt-file, or persistent
configuration is currently implemented.

## Application code organization

The application remains one Java process and one Gradle project. Its internal
application layer uses small package-private collaborators instead of placing
every command in the public entry point:

- `KaosApplication` owns JVM startup, shutdown, configuration loading, and the
  stable process exit boundary.
- `ApplicationRuntime` constructs the command graph from explicit dependencies.
- `CommandContext` carries validated configuration and process input/output.
- `CommandRouter` parses the supported CLI shape and selects one command.
- `KnowledgeIngestCommand`, `OllamaStatusCommand`, `OllamaModelCommand`,
  `OllamaPromptCommand`, and `ConversationCommand` coordinate one application
  workflow each; domain and infrastructure behavior
  remains in `io.kaos.knowledge`, `io.kaos.ai.ollama`, and
  `io.kaos.conversation`.
- `OllamaPromptSubmission` is the narrow application port used to substitute a
  deterministic prompt implementation in tests.
- `ErrorReporter` owns the stable coded-error format shared by commands.
- `io.kaos.tool.readlocalfile` owns the complete first-tool capability below
  the `io.kaos.tool` parent namespace. Add a sibling package only when another
  concrete tool is implemented; do not add a registry or shared tool framework
  solely because more tools are planned.

When adding a command, keep its parsing in `CommandRouter`, put its workflow in
a named command coordinator, supply external behavior through its constructor,
and test the coordinator directly plus the public CLI contract. Use
package-private visibility unless another package is a demonstrated consumer.
Do not add a framework, module, repository, or service merely to organize the
code; follow the capability-boundary evidence rules before promoting the
boundary.

These are responsibility rules, not line-count quotas. A growing class should
be split when it gains another reason to change, mixes parsing/presentation with
domain behavior, or needs an expanding set of test-only overloads.

## Run tests

Run every test:

```powershell
./gradlew.bat test --no-daemon
```

Run the document-ingestion package and its command-boundary tests:

```powershell
./gradlew.bat test --tests 'io.kaos.knowledge.*' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
```

These tests use temporary local files only. They exercise exact UTF-8 bytes,
the one MiB boundary, invalid types and content, non-regular and unavailable
inputs, exact Unicode chunk boundaries and overlap, bounded immutable output,
safe CLI metadata, and privacy-safe error mapping.

Run only the deterministic temporary-SQLite schema and storage tests:

```powershell
./gradlew.bat test --tests 'io.kaos.conversation.*Test' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
```

These tests create temporary database files, initialize and validate schema
version 1, exercise conversation and message storage, resolve the application
path, and prove bounded session reconstruction. They also classify synthetic
SQLite result codes, hold a real exclusive database lock, preserve a real
corrupt file byte-for-byte, and prove that a failed post-answer write rolls back
without claiming the displayed turn was saved.

Run the current application package tests for focused feedback:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon
```

Run the Ollama package and application tests together:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.*' --tests 'io.kaos.app.*' --no-daemon
```

Run only the deterministic application-to-Ollama integration suite:

```powershell
./gradlew.bat test --tests 'io.kaos.app.KaosOllamaIntegrationTest' --no-daemon --warning-mode=all
```

This suite enters through both the `ollama-prompt` and `conversation`
application routes, uses the real `OllamaPromptClient`, sends HTTP only to an
ephemeral loopback server, and consumes streamed NDJSON. It verifies exact
one-shot behavior, selected-conversation isolation, and multiple application
runs against one temporary SQLite database. A clean turn is restored in exact
order into the next real HTTP request; a malformed partial turn remains absent
from both durable history and a later restarted request. The suite does not
contact the fixed production port, require an installed model, use operator
data, or use external network access. See
[persistence integration testing](../evolution/persistence-integration-testing.md).

Run only the real child-process and timeout scenarios:

```powershell
./gradlew.bat test --tests 'io.kaos.app.KaosApplicationProcessTest' --no-daemon
```

Force a focused suite to execute again even when Gradle considers it up to date:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon --rerun-tasks
```

Use `verifyLocal`, not only `test`, before completing an application feature.
It additionally proves compilation, checks, packaging, and the real `status`
and `help` application entry points.

## Useful Gradle commands

| Goal | Windows command |
| --- | --- |
| Compile production source | `./gradlew.bat classes` |
| Run all tests | `./gradlew.bat test` |
| Run checks and create artifacts | `./gradlew.bat build` |
| List verification tasks | `./gradlew.bat tasks --group verification --no-daemon` |
| Inspect full verification order | `./gradlew.bat clean verifyLocal --dry-run --no-daemon` |
| Clean and fully verify | `./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all` |

For Linux/macOS, replace `./gradlew.bat` with `./gradlew`.

## Generated outputs

Gradle creates reproducible output under `build/`, including:

- compiled production and test classes;
- the test report at `build/reports/tests/test/index.html`;
- JAR and distribution artifacts under `build/libs/` and
  `build/distributions/`;
- generated start scripts under `build/scripts/`.

`./gradlew.bat clean` removes the Gradle build output so it can be regenerated.
It does not remove application data because the current application creates no
product state.

## Development workflow

1. Select one active feature under
   [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814).
2. Create the feature branch. Keep all of that feature's stories or direct tasks
   on the same branch.
3. Implement the smallest useful behavior and run focused tests during the
   feedback loop.
4. Update documentation that the behavior actually changes.
5. Update the [living architecture website](../../ui/architecture/index.html)
   when packages,
   dependencies, integrations, data ownership, runtime flows, deployment, or
   architectural status change. Leave it unchanged when the architecture did
   not change.
6. Run the complete clean `verifyLocal` checkpoint.
7. Commit each story or task with its roadmap identifier and issue number, for
   example `feat(STORY-002.01.01): connect to local Ollama #<issue>`.
8. Open one pull request for the completed feature.

Do not create a Git tag or GitHub release unless the user explicitly requests
one.

## Troubleshooting

### Java or the toolchain is unavailable

Run `java -version` and confirm that a Java 21 JDK is installed and available.
Correct `JAVA_HOME` or the shell path when they reference an unavailable or
incompatible runtime, then rerun the wrapper command.

### Gradle cannot resolve dependencies

The first build may require network access for declared build and test
dependencies. Check the reported repository, proxy, TLS, or cache error and
retry after correcting that environment problem. `status`, `help`, and
`ollama-model` make no provider request; `ollama-status` and `ollama-prompt`
make only their documented loopback requests.

### A test or verification task fails

Use the first failing Gradle task and its report rather than the missing final
success message. Correct the source, test, configuration, toolchain, cache, or
dependency problem, then rerun the focused command. Finish with the clean
`verifyLocal` checkpoint.

### Conversation persistence fails

`KAOS-CONVERSATION-002` identifies a safe recovery category without printing
the database path, SQL, driver message, prompt, or answer. Follow its category:

| Category | Operator action |
| --- | --- |
| Locked | Stop other processes using `conversations.db`, then retry deliberately |
| Corrupt | Stop KAOS and make an offline copy before attempting diagnosis or repair |
| Read-only | Grant write access or choose a writable data directory |
| Capacity | Free local disk space before retrying |
| Unavailable | Verify the configured directory exists and is accessible |
| Invalid state | Preserve an offline copy and verify the database/schema state |
| Unknown | Stop, keep the database unchanged, and inspect the local environment |

For an offline copy, first stop KAOS and every process using the database. Copy
`conversations.db` and any adjacent `conversations.db-wal` or
`conversations.db-shm` files together, keep the original untouched, and perform
any repair experiment only on the copy. A startup failure means no session was
opened. A `/new` failure means that identifier was not saved. A failure after
an answer was displayed means that complete turn was not saved and the command
ends. KAOS performs no automatic retry, lock wait, backup, repair, replacement,
or deletion.

### Local configuration changes the run output

Inspect `KAOS_APP_NAME`, `KAOS_OLLAMA_MODEL`, `KAOS_OLLAMA_CONTEXT_WINDOW`,
`KAOS_OLLAMA_THINKING`, `KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`, and
the corresponding `kaos.app.name`, `kaos.ollama.model`,
`kaos.ollama.context-window`, `kaos.ollama.thinking`, or
`kaos.ollama.response-token-limit` system property. The
`verifyLocal` smoke
tasks deliberately supply the safe `KAOS` application name and do not invoke
`ollama-model`, so they remain deterministic without a model selection.

### Ollama model configuration is rejected

Set `KAOS_OLLAMA_MODEL` to one installed model name you intentionally chose,
set `KAOS_OLLAMA_THINKING` to `off` or `on`, and set any explicit response-token
limit to a whole value from 64 through 4,096, then rerun `ollama-model`. Remove
spaces, control characters, empty namespace segments, or unsupported model
punctuation. KAOS does not echo invalid configured values in its error message.
`ollama-prompt` is the first command that asks Ollama to use the configured
model and thinking setting, so an unavailable model or unsupported thinking
request is reported only when that request is submitted.

### An Ollama answer reaches a length boundary

`KAOS-AI-003` means Ollama returned `done_reason: length`. KAOS intentionally
labels any progressively displayed answer as partial. Review both the
response-token limit and context window, then retry with a larger bounded value
only when the request needs it. A larger response limit does not create
additional context capacity.

### Ollama is unavailable

Start Ollama locally and verify that its version endpoint responds at
`http://127.0.0.1:11434/api/version`, then rerun `ollama-status`. Feature 002.01
does not support changing the endpoint, retrying automatically, or connecting
to a remote host. An invalid response should be treated as an Ollama
installation/version problem rather than printed as raw provider data.

For `ollama-prompt`, `KAOS-AI-001` specifically means the request could not
reach Ollama before a response began. Once Ollama accepts the request and starts
a response, a later local transport loss is reported separately as
`KAOS-AI-004`.

### An Ollama prompt fails or times out

First run `ollama-status`, then use `ollama list` to confirm that the configured
model is installed. The prompt diagnostics identify the failed boundary:

| Code | Meaning | Recovery |
| --- | --- | --- |
| `KAOS-AI-001` | Ollama was unreachable before the response began | Start Ollama on loopback and retry |
| `KAOS-AI-002` | Ollama rejected the request or returned invalid response data | Verify the configured model and local Ollama installation |
| `KAOS-AI-003` | Ollama or KAOS reached a provider or local generation limit | Review the context, response-token, and request size bounds |
| `KAOS-AI-004` | An accepted response stream lost its local transport | Verify Ollama is still running before retrying |
| `KAOS-AI-005` | The five-minute total deadline or 60-second inactivity deadline expired | Follow the message to shorten the request, check progress, or select a faster model |
| `KAOS-AI-006` | The command was cancelled | Retry only when ready |

Validated answer content is displayed progressively, but model loading can
still delay the first chunk. KAOS cancels a timed-out stream and reports whether
the complete request deadline or no-data deadline expired. Malformed,
incomplete, or locally oversized streams fail safely. If output was visible,
treat it as partial and do not retry automatically; decide whether a new prompt
is safe. Diagnostics never include the prompt, raw provider body, reasoning,
configured private values, or exception details.

### Stop a running Gradle command

Use the shell's normal interrupt, typically Ctrl+C. While the command is active,
KAOS translates process shutdown into command-thread interruption, cancels the
HTTP response subscription, stops further terminal output, and waits up to two
seconds for resource cleanup. It does not retry or resume the partial response.
The current workflow has no background application worker or product state
requiring rollback. KAOS does not start or own the local Ollama process.

## Detailed references

- [Local run and verification workflow](../evolution/local-run-and-verification-workflow.md)
- [Application test harness](../evolution/application-test-harness.md)
- [Basic command-line interaction](../evolution/basic-command-line-interaction.md)
- [Application configuration](../evolution/application-configuration.md)
- [Package-first application structure](../evolution/package-first-application-structure.md)
- [Capability boundary evolution](../evolution/capability-boundary-evolution.md)
- [Completed work and verified evidence](../evolution/completed-work-and-evidence.md)
- [Persistence failure handling](../evolution/persistence-failure-handling.md)
- [Persistence integration testing](../evolution/persistence-integration-testing.md)
- [Single document-type ingestion](../evolution/single-document-ingestion.md)
- [Text extraction](../evolution/text-extraction.md)
- [Architecture website structure and maintenance](../../ui/architecture/README.md)
- [Ollama connectivity](../evolution/ollama-connectivity.md)
- [Ollama model configuration](../evolution/ollama-model-configuration.md)
- [Ollama context-window benchmark](../evolution/ollama-context-window-benchmark.md)
- [Ollama model scenario benchmark](../evolution/ollama-model-scenario-benchmark.md)
- [Ollama thinking policy and benchmark](../evolution/ollama-thinking-policy-and-benchmark.md)
- [Ollama response-generation limit benchmark](../evolution/ollama-response-generation-limit-benchmark.md)
- [Ollama prompt submission](../evolution/ollama-prompt-submission.md)

The detailed references retain acceptance evidence, internal contracts, and
historical validation. This guide owns the current developer-facing commands
and workflow and must be updated when they change.
