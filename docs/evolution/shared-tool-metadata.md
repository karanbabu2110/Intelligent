# Shared Tool Metadata and Runtime Foundation

Feature [008.05](https://github.com/karanbabu2110/KAOS/issues/896), together with
[008.04](https://github.com/karanbabu2110/KAOS/issues/895) and
[008.06](https://github.com/karanbabu2110/KAOS/issues/897), now has a shared runtime
foundation in the single Java 21 application. It completes the previously minimal
checkpoints and supplies the safe snapshot boundary now used by
[008.07 - Tool Execution History](https://github.com/karanbabu2110/KAOS/issues/899).
Feature [008.08 - Tool Contract Refinement](https://github.com/karanbabu2110/KAOS/issues/898)
subsequently reviewed this boundary against its concrete consumers and retained
it without another runtime abstraction; see the
[refinement decision](tool-contract-refinement.md).

## Current architecture

```text
User question -> Ollama response -> ToolSelector -> ToolSelection
                                      |                |
                               explicit allowed set   ToolRegistry-resolved KaosTool
                                                       |
                                         concrete validation / configuration
                                                       |
                                           ToolPermissionPolicy
                                                       |
                                         exact approval / concrete grant
                                                       |
                                            concrete executor
                                                       |
                                              ToolResult
                                                       |
                                      one no-tools model continuation
```

A direct answer bypasses tool configuration and execution. This is not a plugin
framework. There is no dynamic discovery, class loading, ServiceLoader, reflection
scan, global mutable registry, external catalog, planner, ranking engine, retry
chain, or autonomous execution loop. The current build uses Java's application
plugin and explicit constructor composition, not Spring Boot; this refactor adds
no Spring dependency or new module.

| Shared type | Responsibility |
| --- | --- |
| `KaosTool<R>` | Descriptor, model definition, concrete argument decoding, local configuration status, and preparation of an exact permission request |
| `ToolDescriptor` | Immutable stable name, human-facing purpose, and approval indication |
| `ToolRegistry` | Constructor-supplied tools, ordered read-only lookup, deterministic duplicate-name rejection |
| `StandardTools` | Explicit construction of the three adapters and immutable operation scopes; creates independent registries, not a service locator |
| `ToolSelector` | Advertised definitions and validation of one model-proposed call against the explicit operation scope |
| `ToolSelection` | Validated identity and copied arguments bound to the resolved implementation; no approval authority |
| `ToolPermissionPolicy<R>` | One decision and attempt around existing concrete approvals/grants; safe lifecycle snapshot |
| `ToolPermissionDecision`, `ToolExecutionOutcome` | Shared decision and lifecycle vocabulary |
| `ToolResult<R>` | Typed request association and private model-result encoding; not a history payload |

`ReadLocalFile`, `HttpGet`, and `WebSearch` are small adapters around existing
contracts, validators, approval objects, and executors. Request types and results
remain concrete immutable values. There is one request type parameter rather
than a hierarchy of generic target, validator, grant, executor, and result types.

## Metadata and composition

Concrete `*ToolContract.NAME` constants remain the stable-name owners. The
adapter descriptor references that constant; it owns the short catalog purpose.
Model descriptions and JSON schemas remain in concrete contracts and are not
merged with human-facing descriptions. Definitions and selected arguments are
returned as independent JSON values, so callers cannot mutate retained state.

Application composition defers registry construction until a tool operation runs,
so baseline `status` and `help` retain their lightweight startup.
`ToolCatalogCommand` iterates the supplied registry in registration order: file,
HTTP, search. It renders descriptors and local configuration syntax status. It
never prepares a permission request, opens file content, resolves DNS, contacts
SearXNG, accesses the public web, or grants authority. Expected configuration
errors render as `unavailable`; unexpected programming errors propagate.

`OllamaPromptClient` uses the same selector for advertisement and decoding within
a request. Registry membership does not advertise a tool: each caller supplies
an explicit allowed list. HTTP remains outside shared conversation/search
selection. See [selection](tool-selection.md) and [discovery](tool-discovery.md).

To add a concrete tool, implement a small adapter in its capability package,
register it at the composition boundary, and deliberately choose the operations
that may advertise it. The catalog, stream selection, and generic result
continuation require no new name/type dispatch branch. Resource-specific errors
and user instructions still require deliberate integration. Legacy typed Ollama
constructors/accessors and explicit file/HTTP commands remain compatibility
boundaries; new tools need not extend those APIs.

## Historical checkpoint and evidence

The original merged `2aaa0ad` checkpoint reused concrete name constants in the
catalog. Its 50 focused tests passed, and full verification passed 418 tests:
414 passed, four Windows symlink skips, zero failures/errors. It deliberately
deferred a registry. This follow-up supersedes that architectural deferral while
retaining the public names, catalog wording, ordering, and configuration behavior.

`ToolRuntimeTest` now verifies registry isolation, duplicates, selection scopes,
immutable exposure, and shared lifecycle security. A fourth test-only
`FixtureTool` passes through the catalog, real local Ollama HTTP fixture,
selection, approval, execution, and continuation without production dispatch
changes. Existing integration tests retain concrete security and persistence
coverage. See [permission policies](tool-permission-policies.md) for the focused
and full verification commands and current results.
