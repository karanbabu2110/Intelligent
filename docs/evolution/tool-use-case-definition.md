# First Tool Use-Case Definition

Feature [007.01](https://github.com/karanbabu2110/KAOS/issues/884) selects the
smallest user-visible tool outcome for Epic 007 without claiming that tool
invocation, approval, file access, or model-result continuation exists yet.

## Decision

The first KAOS tool will be named **`read_local_file`**. It will let the local
model request the text of one user-owned, text-based file so that it can answer
the user's current question about that file. KAOS will execute the read only
after validating the request and receiving explicit approval for the exact
resolved target.

The first end-to-end use case is:

1. the user asks the local model to explain or summarize a named source or text
   file;
2. the model requests `read_local_file` with one relative path;
3. KAOS validates the request against one explicitly configured local read
   root and shows the resolved target and disclosure consequence;
4. the user approves or denies that single read;
5. after approval, KAOS reads and validates the bounded file once, returns its
   text to the model as untrusted tool data, and presents the model's answer;
6. KAOS records content-free audit evidence of the request and outcome.

This is the first tool because it produces useful information for the current
answer, remains local with the existing loopback-only Ollama boundary, and can
reuse the application's proven bounded-file techniques without introducing a
network, process-execution, persistence, or destructive-action boundary.

## Product contract

| Concern | First-tool decision |
| --- | --- |
| User | The local person running the KAOS process |
| Tool name | Exactly `read_local_file` |
| User outcome | Ask the local model about one approved local text-based file |
| Requested input | One non-blank relative path supplied in a model tool request |
| Permission scope | One file below one explicitly configured local read root |
| Approval | Required for every invocation and bound to the exact resolved target |
| Execution output | Bounded validated text plus the minimum source metadata required to associate it with the request |
| AI use | Tool output is marked as untrusted data and supplied only to the continuing local Ollama request |
| Persistence | None; file bytes and extracted text are foreground-request data only |
| Audit | Operation, content-free target identity, decision, and outcome; never file content |
| Ownership | The user owns the file and approval decision; KAOS owns validation, bounded reading, tool-result construction, and safe diagnostics |
| Lifetime | One foreground invocation; no implicit ingestion, indexing, caching, or later reuse |

The exact machine-readable argument and result shapes belong to Feature 007.02.
They must preserve this single-path, single-result decision rather than expose a
general filesystem API.

## Initial format boundary

The first implementation is for strict UTF-8 text-based files whose filename
uses one explicitly supported extension:

- `.txt`, `.md`, and `.log` for ordinary text;
- `.java`, `.kt`, `.kts`, and `.gradle` for the current JVM development use
  case;
- `.json`, `.xml`, `.yaml`, `.yml`, and `.properties` for structured text; and
- `.csv` for bounded tabular text.

An extension only makes a file eligible; it does not prove that its bytes are
text. The read must reject malformed UTF-8, NUL-containing or otherwise binary
content, empty files, non-regular files, symbolic links or equivalent link
escapes, and files above the tool-result limit.

Binary and compound document formats are deliberately unsupported. This
includes `.doc`, `.docx`, `.pdf`, images, audio, archives, executables, and
databases. Supporting a format that needs parsing, archive expansion, OCR, or
media interpretation requires a later evidence-backed use case and its own
resource and parser safety review.

## Resource boundary

The existing knowledge-ingestion ceiling of 1 MiB is not the
`read_local_file` limit. Knowledge ingestion can chunk a document before later
use, while this tool must place its complete result into a continuing model
request whose ordinary context window is currently 4,096 tokens.

Feature 007.02 must select and test one conservative input/result ceiling that
reserves space for the user's request, tool definition, model tool call,
protocol framing, and final answer. The first implementation must reject a file
that cannot fit the selected ceiling; it must not silently truncate content or
claim that a partial file is complete.

There is no multi-file read, directory listing, recursive traversal, streaming
file tail, pagination, range request, archive expansion, or automatic retry in
the first use case.

## Authority and approval

The user's question is not permission to read a file. The model's tool request
is also not permission. KAOS must present a separate approval prompt that:

- names `read_local_file`;
- identifies the exact normalized target without exposing unrelated paths;
- states that the file text will be supplied to the configured local Ollama
  model for the current answer; and
- accepts only an unambiguous approval response.

Denial, end-of-input, interruption, an unrecognized response, or a changed
target defaults to no read and no model continuation with file content. An
approval authorizes one attempt against one target; it is not a session-wide,
directory-wide, extension-wide, or future-request permission.

Feature 007.05 owns the executable path-containment and permission rules. At a
minimum, the runtime must use an explicitly configured read root, reject
absolute and escaping paths, avoid following links, and revalidate the target
after approval before consuming content. If it cannot establish that the file
still represents the approved allowed target, it must fail closed.

## Data flow and trust boundary

```text
user question
  -> local Ollama request with one fixed tool definition
  <- model requests read_local_file(relative path)
  -> KAOS validates requested target without reading its content
  -> user approves the exact read
  -> KAOS revalidates and reads one bounded file
  -> validated text is returned as untrusted tool data to local Ollama
  <- final model answer
  -> content-free audit outcome
```

File content is user-owned and potentially confidential. It leaves the Java
process only for the already configured loopback Ollama request. KAOS must not
print the complete content as a diagnostic, persist it in conversation,
knowledge, memory, or audit storage, or send it to an external endpoint.

The file is also untrusted model input. Source comments, Markdown, structured
text, and logs may contain prompt-injection instructions. KAOS must distinguish
tool data from application and user instructions in the provider protocol and
must never interpret file text as permission for another tool call. This
boundary reduces risk but cannot guarantee that a model will summarize hostile
content correctly; model output remains untrusted.

## Failure and recovery

- Unknown tool names, unexpected arguments, unsupported extensions, unsafe
  paths, unavailable read-root configuration, and targets outside the allowed
  root fail before approval.
- Missing, unreadable, empty, linked, non-regular, changed, oversized, binary,
  or malformed UTF-8 files fail without returning partial content to the model.
- Denial and cancellation are observable non-execution outcomes, not errors
  that trigger a retry.
- Provider failure before a tool request performs no file read.
- Provider failure after an approved read does not persist the file content or
  repeat the read automatically.
- Public errors identify the recovery action without printing file content,
  provider bodies, stack traces, or unrelated absolute paths.
- Retrying requires a new model request and a new per-invocation approval.

Because the operation is read-only, recovery is normally to correct the path,
configuration, encoding, type, or size and make a new request. KAOS makes no
file modification that needs rollback.

## Relationship to existing capabilities

This tool is not knowledge ingestion. It does not chunk, embed, store, rank,
retrieve, cite, or retain the file. The existing `TextDocumentIngestor`
demonstrates bounded reads, no-follow handling, strict UTF-8 validation, and
privacy-safe errors, but its `.txt` and 1 MiB contract is not automatically the
tool contract.

This tool is not unrestricted filesystem access. It cannot list directories,
discover files, inspect metadata broadly, write, rename, delete, execute, or
watch anything. It also performs no HTTP request and introduces no new module,
service, plugin system, repository, worker, event bus, or parser dependency.

Implementation remains direct in-process code in the single application.
Later features may extract a boundary only if the completed tool path supplies
current evidence for one.

## Safety classification and review

The eventual runtime behavior is **Guarded** because it introduces user-owned
content, executable tool use, prompt/tool-injection exposure, and bounded
filesystem reach. The affected asset is one approved local file; the affected
person is the local user. There is no external recipient under the fixed
loopback Ollama configuration.

The decision is to proceed only with the narrowed text-based, configured-root,
per-invocation approval design. General filesystem access, implicit permission,
binary document parsing, remote providers, persistent content, chained tools,
and automatic retries are deferred.

## Acceptance and validation for this feature

This decision artifact is the deliverable for Feature 007.01. Inspection must
show one demonstrable user outcome, input and result boundaries, ownership,
lifecycle, approval semantics, data flow, threat and failure cases,
least-privilege constraints, recovery, and explicit exclusions.

Documentation link checks and `git diff --check` are proportional because this
feature changes no runtime or build behavior. It creates no command, tool
schema, filesystem configuration, Ollama request change, audit storage, test
fixture, dependency, or runtime capability claim.

## Deliberate limits and handoff

Feature 007.01 selects the use case only. It does not prove that KAOS or the
configured model can call tools. Support for compound or binary documents,
multiple files, directories, arbitrary extensions, non-UTF-8 encodings,
truncation, persistence, remote models, or tool chaining is not implied.

The next ordered feature is
[007.02 - Minimal Tool Contract](https://github.com/karanbabu2110/KAOS/issues/883).
It should define and verify only the fixed `read_local_file` request/result
contract and conservative payload ceiling needed by this selected behavior.
