# Tool Permission Policies

Feature [008.06](https://github.com/karanbabu2110/KAOS/issues/897), under
[Epic 008](https://github.com/karanbabu2110/KAOS/issues/63) and roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814), now implements a shared approval/execution lifecycle around the existing
three-tool permission contract. Configuration and concrete resource checks are
unchanged; EOF and interruption handling are strengthened.

## Decision

Retain concrete resource policies with a common operating rule: a model may
propose one request, but execution requires valid configuration, a permitted
target, and explicit local-user approval for that exact attempt. Configuration,
discovery, model selection, and the catalog's `approval: required` text do not
grant execution authority.

| Tool | Operator-controlled scope | Before approval | Approved attempt |
| --- | --- | --- | --- |
| `read_local_file` | One configured local read root | Validate path containment and file metadata; display exact resolved file and size | Claim grant, revalidate identity, read bounded UTF-8 text, revalidate again |
| `http_get` | Exact allowed HTTPS host names | Validate URL syntax and allowed host; display exact URL without DNS or HTTP | Claim grant, check interruption and public DNS addresses, perform one bounded GET without redirects |
| `web_search` | Configured SearXNG origin and operator-managed upstream engines | Validate bounded query and service configuration; display exact query and external-disclosure notice | Claim grant, check interruption, send one bounded search request to configured service |

The file path must identify a readable, nonempty regular file within the root;
symbolic-link segments and oversized targets are rejected. Its metadata is
inspected before approval, but content is not opened until approval. Metadata
revalidation reduces replacement risk; it is not an atomic filesystem snapshot.

HTTP accepts standard-port HTTPS on an explicitly allowed host, without URL
credentials or fragments. The post-approval DNS check rejects non-public
addresses. As documented in the [HTTP tool record](http-get-tool.md), the check
and the HTTP client's connection are separate: complete DNS-rebinding resistance
is not claimed.

SearXNG is trusted configured infrastructure and may use a loopback/private
origin, HTTP or HTTPS, and a configured port. Applying the HTTP retrieval
public-host policy to it would prevent the supported local service setup.
The model controls only the query, never the endpoint or engine configuration.
Search result URLs are not fetched. See the [search record](web-search-tool.md).

## Approval lifecycle and failure behavior

The documented tokens are case-sensitive `approve` and `deny`, with surrounding
whitespace stripped. A request can be decided once. Approval produces a grant
bound to the displayed concrete target or query; the executor claims it once.
Denial, unrecognized input, end of input, and cancellation grant no authority.
An attempt consumes its grant even if execution subsequently fails. Recovery
requires a new request and approval, with no automatic fallback or retry.

All concrete approval objects share `ToolPermissionDecision.parse`, which
checks interruption and exact case-sensitive tokens. The shared lifecycle also
checks interruption after input is read. Unterminated approval text at EOF grants
no authority. Input I/O errors cancel the pending request. Each
executor also checks interruption before its protected operation. Cancellation
is cooperative and cannot undo an already completed read or external request.
Concrete public decision enums remain compatibility adapters to the shared
decision vocabulary.

Invalid targets fail before execution; unavailable dependencies and execution
failures retain their existing concrete diagnostic categories. A successful
tool result is supplied to one model continuation that advertises no tools.
Another call is rejected under the [selection contract](tool-selection.md).

The local approval prompt intentionally reveals the target or query needed for
an informed decision. General diagnostics and audit records omit private
targets and content. Approved file text and HTTP content reach configured
Ollama; search queries reach SearXNG and potentially its external engines.
There is no persistent approval, session-wide grant, trust-by-tool-name option,
policy file, automatic approval, or permission storage.

## Ownership and evolution boundary

`ReadLocalFilePermissionValidator`, `HttpGetPermissionValidator`, and
`SearxngClient` still own resource rules. `ReadLocalFile`, `HttpGet`, and
`WebSearch` adapt their existing approval/grant/executor boundaries.
`ToolPermissionPolicy<R>` coordinates one validated request:

```text
APPROVAL_REQUIRED -> APPROVED -> EXECUTING -> SUCCEEDED
        |                            |
        +-> DENIED / INVALID         +-> FAILED / CANCELLED
        +-> CANCELLED (including EOF)
```

A request exists in this lifecycle only after successful configuration and
resource validation. Selection/configuration/validation failures retain their
separate existing diagnostic categories; they do not fabricate approval or an
execution record. There is no persisted state machine.

The policy verifies that the concrete decision agrees with local input, retains
the concrete grant only behind an approved execution callback, and consumes that
callback before invoking the executor. Concrete executors still claim their
original single-use grants. Repeated decisions, repeated attempts, and concurrent
attempts are rejected. Execution failures are observed and rethrown; unexpected
programming errors are not converted into ordinary tool failures.

`ToolPermissionPolicy.Snapshot` exposes only the stable name, opaque operation
UUID, decision (absent until decided), lifecycle outcome, and start/update
instants. [Tool Execution History](tool-execution-history.md) now converts each
terminal snapshot into a bounded local record. Existing audit rendering uses
the same operation UUID. It includes no
path, query, configured host/endpoint, prompt, result, exception detail, or file
content. A future history consumer can read it at lifecycle boundaries. Tool
success is recorded before model continuation, so model failure cannot relabel
a successful tool execution as a failed tool attempt.

Selection, configuration, and validation failures before a permission lifecycle
remain separate diagnostics and are intentionally absent from terminal execution
history. `ToolResult` is private model data and is never passed to the history
store. No observer bus or persisted state machine was added. This is not a
plugin framework; dynamic discovery is not implemented.

## Verification

Existing file tests cover containment, approval denial/cancellation/end of
input, grant reuse, revalidation, and execution failures. HTTP contract tests
cover exact hosts, post-approval DNS rejection, and single-use grants. Search
tests cover exact query approval, cancellation, grant reuse, endpoint ownership,
and failures. Application tests prove approved dispatch and denied boundaries.

The original checkpoint added HTTP application tests for invalid tokens, EOF,
and interrupted approval. This follow-up adds shared lifecycle tests for all
three adapters, successful and failed single-use attempts, interruption before
and after approval, and safe snapshots. Command tests also reject EOF after an
unterminated `approve` token and interruption while reading approval. Registry,
selection, catalog extension, and generic continuation tests use a fourth fixture
tool without modifying production dispatch.

```powershell
./gradlew.bat test --tests 'io.kaos.tool.*' --tests io.kaos.ai.ollama.OllamaPromptClientTest --tests io.kaos.app.ToolCatalogCommandTest --tests io.kaos.app.ReadLocalFileCommandIntegrationTest --tests io.kaos.app.HttpGetCommandIntegrationTest --tests io.kaos.app.WebSearchIntegrationTest --no-daemon --console=plain
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all --console=plain
```

Tests use local temporary files and service doubles. They establish deterministic
permission boundaries, not real-model source quality or public-network safety
against every timing attack. For operation and configuration, see the
[developer guide](../development/developer-guide.md).

Historical validation of merged `9e0d397` on 2026-09-09: the focused selection
completed 96 tests, with 93
passed, three existing Windows symlink skips, and no failures or errors. Full
`clean verifyLocal` passed all 11 tasks with 420 tests: 416 passed, four existing
Windows symlink skips, and no failures or errors. Changed local links and
whitespace checks passed.

Current runtime-refactor focused verification: 154 tests across 18 suites;
151 passed, three existing Windows symlink skips, zero failures and errors.
The focused command above includes registry/lifecycle, provider, catalog, and
all three command integration suites.

The first full run exposed three baseline process failures caused by eager
registry construction requiring Jackson on the minimal status-test classpath.
Registry composition is now deferred until tool use. The targeted startup,
catalog, and shared-command regression run passed 21 tests without skips or
failures. This fixes the regression rather than expanding the baseline test
classpath.

Final full verification passed all 11 tasks in 20 seconds: 439 tests across
60 suites, 435 passed, four existing Windows symlink skips, zero failures and
errors. Build, packaging, status, and help all passed. The skipped cases are the
knowledge-ingestion symlink case and file-policy linked-root, linked-target, and
linked-ancestor cases; they remain covered where the platform permits link creation.

After merge, the next ordered feature is
[008.07 - Tool Execution History](https://github.com/karanbabu2110/KAOS/issues/899).
