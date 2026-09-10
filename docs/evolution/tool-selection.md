# Bounded Tool Selection

Feature [008.04](https://github.com/karanbabu2110/KAOS/issues/895), under
[Epic 008](https://github.com/karanbabu2110/KAOS/issues/63) and roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814), now provides runtime
selection through `ToolSelector` and `ToolSelection`. The original documentation
checkpoint is completed by the [shared runtime foundation](shared-tool-metadata.md).

## Model selection and operation authority

The current Ollama model chooses whether to answer directly or propose one tool.
There is no keyword classifier, confidence score, relevance ranking, fallback
planner, or autonomous loop. `LocalToolsCommand` guides the model toward local
files for project information and search for current public facts. Guidance does
not prove that the chosen source is necessary, optimal, or sufficient.

| Entry point | Available selection | Execution boundary |
| --- | --- | --- |
| `web-search`, `conversation` | Direct answer, `read_local_file`, or `web_search` | At most one validated and approved tool |
| `read-local-file` | Direct answer or `read_local_file` | Existing file policy and approval |
| `http-get` | Direct answer or `http_get` | Existing HTTPS policy and approval |
| Successful result continuation | Answer only | No second tool call |
| `tools` | User catalog only | No selection or execution |

`StandardTools` supplies immutable operation lists. `ToolSelector` receives the
registry and an explicit list, derives only those definitions, checks the
proposed name against registry membership and operation authority, and delegates
argument decoding to the resolved tool's concrete contract. A registered tool
can still be disallowed. Configuration readiness does not filter advertisement;
a direct answer does not load any tool configuration.

`OllamaPromptClient` owns JSON/NDJSON parsing, stream limits, thinking/answer
validation, and the one-call limit across the complete stream. `ToolSelector`
validates the function-call shape and returns an empty selection for no call.
The provider must still validate the direct answer. A nonempty `ToolSelection`
contains the resolved identity, immutable request, and defensively copied JSON
arguments. It is not a grant. Preparing it performs the concrete configuration
and resource checks before displaying an exact approval request.

`LocalToolsCommand` executes through the selected adapter and shared permission
lifecycle instead of branching on file versus search. Existing explicit file and
HTTP commands retain their CLI diagnostics and typed submission seams, while
using the same lifecycle and concrete adapters. Every command retains its own
allowed-tool boundary.

## Rejection and continuation

Unknown tools, registered-but-disallowed tools, malformed arguments, invalid
call envelopes, multiple calls, and mixed answer/tool responses execute nothing.
`ToolSelectionException.Reason` distinguishes the first three from invalid
response shape. The Ollama result retains a safe `selectionFailure` category
while preserving the existing `INVALID_RESPONSE` provider status. No generated
arguments or unknown model-supplied names enter these diagnostics.

Denial, invalid approval, EOF (including an unterminated `approve` line), or
interruption grants no execution authority. A successful result must match the
pending tool name and typed request. Its one model continuation advertises no
tools and rejects any returned call. A failed execution or continuation never
silently retries. Continuation failure cannot undo a completed protected action.

Search discovers URLs; HTTP retrieves one approved resource under a separate
policy. Registry membership does not broaden shared network authority. Search
results are not fetched automatically; use `http-get` explicitly for retrieval.
This is not a plugin framework, and dynamic discovery is not implemented.

## Privacy and intentional limits

Tool schemas do not disclose private configuration values. Approved file text and
HTTP results reach configured local Ollama; search queries reach SearXNG and
possibly its configured upstream engines. Results are untrusted data. Rejecting
subsequent calls is an enforced boundary; answer correctness and resistance to
misleading source text are not guaranteed.

Clean conversation direct-answer turns still use retained message history and
are persisted. Tool-backed message turns remain non-persistable, and their
continuation includes the current prompt and result without replaying earlier
conversation history. Their content-free terminal execution metadata is stored
separately under [Tool Execution History](tool-execution-history.md).

## Evidence

The original `5cd6025` documentation checkpoint passed 47 tests (36 provider and
11 application tests), with no failures/errors/skips. It introduced no runtime
changes. This follow-up adds runtime coverage rather than relying on that record.

`OllamaPromptClientTest`, `ToolRuntimeTest`, and `WebSearchIntegrationTest` verify
direct answers, explicit sets, a fourth fixture capability, selection failure
categories, immutable arguments, multiple calls, rejected chaining, denied and
cancelled approval, selected-tool configuration failures, and unchanged
conversation persistence. All use local fixtures, not live Ollama, SearXNG, or
public APIs. Current commands/results are in [permission policies](tool-permission-policies.md).

Feature [008.07 - Tool Execution History](https://github.com/karanbabu2110/KAOS/issues/899)
now consumes terminal lifecycle snapshots without changing this bounded
selection contract.
