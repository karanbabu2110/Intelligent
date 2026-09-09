# Tool Permission Policies

Feature [008.06](https://github.com/karanbabu2110/KAOS/issues/897), under
[Epic 008](https://github.com/karanbabu2110/KAOS/issues/63) and roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814), records the current
three-tool permission contract and strengthens HTTP command verification.
Production behavior and configuration are unchanged.

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

File and HTTP commands handle interrupted approval through their explicit
cancel operation; search checks interruption in its approval decision. Each
executor also checks interruption before its protected operation. Cancellation
is cooperative and cannot undo an already completed read or external request.
The APIs need not share a status class to enforce this operating rule.

Invalid targets fail before execution; unavailable dependencies and execution
failures retain their existing concrete diagnostic categories. A successful
tool result is supplied to one model continuation that advertises no tools.
Another call is rejected under the [selection contract](tool-selection.md).

The local approval prompt intentionally reveals the target or query needed for
an informed decision. General diagnostics and audit records omit private
targets and content. Approved file text and HTTP content reach configured
Ollama; search queries reach SearXNG and potentially its external engines.
There is no persistent approval, session-wide grant, trust-by-tool-name option,
policy file, automatic approval, or new permission storage in this checkpoint.

## Ownership and evolution boundary

`ReadLocalFilePermissionValidator`, `HttpGetPermissionValidator`, and
`SearxngClient` own their concrete scope checks. Their approval/grant types own
one-decision and one-attempt authority. Application commands own the prompt,
input, outcome reporting, and continuation sequence.

A generic policy engine would need to encode the same distinct target types,
validation timing, and destination rules. No current consumer requires that
indirection. Revisit it when a demonstrated shared policy must be changed in
multiple places and cannot be expressed clearly by these concrete owners.
Execution history remains the next feature, not a new dependency for approval.

## Verification

Existing file tests cover containment, approval denial/cancellation/end of
input, grant reuse, revalidation, and execution failures. HTTP contract tests
cover exact hosts, post-approval DNS rejection, and single-use grants. Search
tests cover exact query approval, cancellation, grant reuse, endpoint ownership,
and failures. Application tests prove approved dispatch and denied boundaries.

This checkpoint adds HTTP application tests that reject invalid tokens and
end of input, and cancel an interrupted request even with `approve` waiting.
Their executor and continuation doubles fail immediately if invoked.

```powershell
./gradlew.bat test --tests 'io.kaos.tool.readlocalfile.*' --tests 'io.kaos.tool.httpget.*' --tests 'io.kaos.tool.websearch.*' --tests io.kaos.app.ReadLocalFileCommandIntegrationTest --tests io.kaos.app.HttpGetCommandIntegrationTest --tests io.kaos.app.WebSearchIntegrationTest --no-daemon --console=plain
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all --console=plain
```

Tests use local temporary files and service doubles. They establish deterministic
permission boundaries, not real-model source quality or public-network safety
against every timing attack. For operation and configuration, see the
[developer guide](../development/developer-guide.md).

Validation on 2026-09-09: the focused selection completed 96 tests, with 93
passed, three existing Windows symlink skips, and no failures or errors. Full
`clean verifyLocal` passed all 11 tasks with 420 tests: 416 passed, four existing
Windows symlink skips, and no failures or errors. Changed local links and
whitespace checks passed.

After merge, the next ordered feature is
[008.07 - Tool Execution History](https://github.com/karanbabu2110/KAOS/issues/899).
