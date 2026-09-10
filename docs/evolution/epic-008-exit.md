# Epic 008 Exit — Multi-Tool Capability

## Outcome

Epic [008 — Multi-Tool Capability](https://github.com/karanbabu2110/KAOS/issues/63)
generalizes tool support only after three concrete tools reveal a stable common
contract. KAOS can advertise an operation-specific bounded tool set, validate
one model-selected call, resolve its implementation, obtain exact user
approval, execute once with a concrete grant, continue the model without tools,
and store a content-free terminal history record.

## Completed capability chain

| Feature | Verified contribution | Evidence |
| --- | --- | --- |
| 008.01 | Added one explicitly allowed, approved, bounded HTTPS GET | [HTTP GET tool](http-get-tool.md) |
| 008.02 | Added one approved bounded SearXNG search | [Web search tool](web-search-tool.md) |
| 008.03 | Added a deterministic read-only tool catalog | [Tool discovery](tool-discovery.md) |
| 008.04 | Added registry-resolved model selection with explicit operation scopes | [Tool selection](tool-selection.md) |
| 008.05 | Added shared immutable descriptors and a static registry | [Shared metadata](shared-tool-metadata.md) |
| 008.06 | Added the shared one-decision, one-attempt permission lifecycle | [Permission policies](tool-permission-policies.md) |
| 008.07 | Added bounded content-free SQLite terminal history | [Execution history](tool-execution-history.md) |
| 008.08 | Reviewed and retained the proven small contract | [Contract refinement](tool-contract-refinement.md) |

## Demonstrable runtime

- `tools` lists the three registered implementations without executing them.
- `read-local-file`, `http-get`, and `web-search` retain explicit bounded
  operation scopes and can answer directly or execute their one allowed tool.
- `conversation` advertises only `read_local_file` and `web_search`; registry
  membership does not expose `http_get` there.
- every concrete request requires its own exact approval and single-use grant;
  a model-selected name never conveys authority.
- a successful execution has one result continuation with no tools advertised.
- `tool-history` displays the newest 20 of at most 1,000 content-free terminal
  records without loading tool configuration or accessing a target.

## Exit evidence

- All eight feature issues are closed and their delivery pull requests merged.
- The final release tree completed all 11 `verifyLocal` tasks.
- 454 tests ran across 62 suites: 450 passed, no failures or errors, and four
  existing Windows symbolic-link skips.
- A fourth test-only tool traverses catalog, local Ollama response parsing,
  selection, approval, execution, and continuation without production dispatch
  changes.
- Deterministic tests use temporary local files, SQLite databases, and loopback
  HTTP fixtures; they require no public internet, live Ollama, live SearXNG, or
  external API.

## Safety, privacy, and recovery

- File root containment, no-link rules, metadata validation, bounded strict
  UTF-8 reads, and revalidation remain specific to `read_local_file`.
- `http_get` retains standard-port HTTPS, exact allowed hosts, public-address
  validation, no redirects, and bounded strict UTF-8 responses.
- `web_search` retains a configured SearXNG endpoint, bounded query, exact
  external-disclosure approval, and bounded result parsing.
- Denial, invalid approval, end of input, and cancellation never grant access.
- Grants are single-use, failed execution does not retry, and a continuation
  cannot request another tool.
- History stores no arguments, paths, URLs, queries, prompts, configuration,
  file contents, or external response bodies.

## Deliberate limits

Epic 008 does not provide runtime plugin loading, reflection discovery,
`ServiceLoader`, automatic registry advertisement, tool ranking, fallback,
retry, autonomous planning, multi-tool chaining, browser automation, an event
bus, remote telemetry, another Gradle module, or a separate service.

## Release and handoff

The completed epic is assigned to
[v1.7.0 — Multi-Tool Capability](https://github.com/karanbabu2110/KAOS/milestone/7)
and released cumulatively as `v1.7.0`. The next ordered feature is
[009.01 — Bounded Agent Use Case](https://github.com/karanbabu2110/KAOS/issues/900).
It remains inactive until explicitly started.
