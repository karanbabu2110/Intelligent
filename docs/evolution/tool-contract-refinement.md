# Tool Contract Refinement

Feature [008.08](https://github.com/karanbabu2110/KAOS/issues/898) reviews the
shared tool runtime after three concrete tools and execution history have used
it. The evidence supports retaining the current contract. No additional runtime
type, module, or framework is needed.

## Decision

The stable shared contract remains:

```text
explicit operation scope
        |
ToolSelector -> ToolSelection -> registered KaosTool
                                      |
                         concrete request validation
                                      |
                         ToolPermissionPolicy
                                      |
                    concrete approval and grant
                                      |
                         concrete executor
                                      |
                            ToolResult
                                      |
                 content-free terminal snapshot
```

`read_local_file`, `http_get`, and `web_search` demonstrate the same shared
needs: stable identity and metadata, a model definition, argument decoding,
configuration status, preparation of an exact permission lifecycle, and a
bounded result for one model continuation. Their request types, target checks,
approval scopes, grants, executors, result shapes, and failure categories remain
concrete.

The current `KaosTool<R>` type parameter is the smallest useful generic: it
keeps each adapter's decoded request and `prepare` boundary type-safe. Extending
that parameter across target, grant, executor, and result types would make the
common API harder to read without removing a current dispatch branch.

## Evidence from current consumers

The contract is exercised through more than one implementation and more than
one caller:

| Boundary | Current evidence | Refinement decision |
| --- | --- | --- |
| `ToolDescriptor` | Catalog renders all three tools in deterministic registry order | Keep human-facing purpose separate from model descriptions and schemas |
| `ToolRegistry` | Catalog and model selection resolve the same statically composed tools | Keep registration separate from operation-specific advertisement and authority |
| `ToolSelector` / `ToolSelection` | Conversation, search, file, and HTTP entry points use explicit allowed sets | Keep the one-call limit and reject unknown, disallowed, malformed, and invalid responses distinctly |
| `KaosTool<R>` adapters | Three request and permission implementations share the boundary | Keep resource validation and execution inside the concrete capability packages |
| `ToolPermissionPolicy<R>` | All three tools share one-decision, one-attempt lifecycle behavior | Keep concrete validators, approval objects, and single-use grants intact |
| `ToolExecutionRecord` | Feature 008.07 consumes only terminal lifecycle snapshots | Keep history observation content-free and outside the execution authority path |
| `ToolResult<R>` | Successful tools continue the model once with matching request data | Keep private result content out of diagnostics and history |

The test-only `FixtureTool` also passes through catalog advertisement, real
local Ollama response parsing, selection, approval, execution, and continuation
without production name or type dispatch changes. This is sufficient evidence
that another locally composed tool can use the contract.

## Rejected refinements

No current behavior justifies any of these changes:

- a common target, validator, grant, executor, or failure hierarchy;
- an invocation coordinator or persisted state machine around one foreground
  attempt;
- reflection, `ServiceLoader`, dynamic class loading, or external registration;
- automatic advertisement of every registered tool;
- a planner, ranking engine, retry or fallback chain, or multi-tool loop;
- a module, library, service, event bus, or telemetry system; or
- removal of typed compatibility accessors still used by existing command and
  test APIs.

These additions would either flatten security rules that must remain
resource-specific or create an abstraction without a second real consumer.

## Security and ownership retained

Registry membership identifies an implementation; it never grants authority.
The caller still chooses the exact advertised set. A model call creates only a
validated selection. The selected adapter must validate its concrete resource,
obtain exact user approval, and supply its existing single-use concrete grant
before execution.

Denial, invalid approval, end of input, and cancellation cannot execute a tool.
Execution failure does not retry, and a consumed grant cannot be reused. File
containment and revalidation, HTTPS host and public-address rules, SearXNG
endpoint and query disclosure, and bounded response handling remain owned by
their concrete packages.

Terminal history observes stable name, opaque operation identifier, decision,
outcome, and timestamps only. It receives no arguments, paths, URLs, queries,
prompts, configuration values, file contents, or external responses.

## Verification and limitations

This feature changes no runtime behavior. Its evidence is the current source,
the three concrete adapter paths, the fourth test-only adapter, and Feature
008.07's independent history consumer. The previously verified runtime tree
passed 454 tests across 62 suites: 450 passed, four existing Windows symbolic-
link skips, and no failures or errors.

Feature 008.08 uses documentation checks because it records an evidence-backed
contract decision. It does not claim dynamic discovery, autonomous chaining,
general-purpose tool execution, remote history, or a plugin framework.

The next ordered feature is
[009.01 - Bounded Agent Use Case](https://github.com/karanbabu2110/KAOS/issues/900).
It can consume this contract as implemented evidence. Any multi-step goal,
planning, execution-state, or checkpoint abstraction remains owned by Epic 009.
