# Bounded Tool Selection

Feature [008.04](https://github.com/karanbabu2110/KAOS/issues/895), under
[Epic 008](https://github.com/karanbabu2110/KAOS/issues/63) and roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814), records the selection
contract already delivered by the concrete tools. This is a decision checkpoint;
it introduces no runtime behavior or configuration.

## Decision and current capability

Retain model selection followed by direct application dispatch. A user asks one
question through `web-search` or `conversation`. KAOS advertises
`read_local_file` and `web_search`; the model may answer directly or propose
one of those tools with bounded arguments. KAOS validates the response, then
uses the selected tool's existing configuration, validation, approval, and
executor. The model's proposal alone never authorizes execution.

`LocalToolsCommand` supplies the instruction to use local files for project
information, web search for current public facts, and otherwise answer directly.
This is model guidance, not a deterministic relevance classifier. KAOS does not
prove that a selected source is necessary, optimal, or sufficient for the answer.

| Entry point | Available selection | Execution boundary |
| --- | --- | --- |
| `web-search`, `conversation` | Direct answer, `read_local_file`, or `web_search` | At most one validated and approved tool |
| `read-local-file` | Direct answer or `read_local_file` | Existing file policy and approval |
| `http-get` | Direct answer or `http_get` | Existing HTTPS destination policy and approval |
| Successful result continuation | Answer only | No second tool call |
| `tools` | User catalog only | No selection or execution |

The [discovery record](tool-discovery.md) defines the exact advertised sets.
Configuration readiness does not filter those sets. Configuration is loaded
lazily for the selected tool, so a direct answer works without file or search
configuration. A missing selected dependency fails that turn; KAOS does not
silently substitute another tool, retry, or broaden authority.

## Ownership and lifecycle

`OllamaPromptClient.submitWithLocalTools` assembles the two definitions and
decodes the response across the full NDJSON stream. Concrete tool contracts
own argument validation. `LocalToolsCommand.submit` dispatches the resulting
typed request to the existing file command or its concrete search path.
Each tool retains its own exact approval and execution policy.

Unknown or unadvertised tools, malformed arguments, multiple calls, and mixed
answer/tool responses fail before execution. Denial, invalid approval input,
end of input, or cancellation does not grant permission. A successful tool
execution supplies one bounded result to Ollama; the continuation advertises
no tools and rejects a new call. A continuation failure does not undo the
already completed read or search, and does not cause a retry.

Selection introduces no persistent state. In conversation, clean direct-answer
turns use retained history and remain persistable. Tool turns are not persisted;
their continuations carry the current prompt and result, without replaying the
earlier conversation history. This existing limitation remains explicit.

## Why HTTP retrieval stays explicit

Search discovers public URLs; HTTP GET retrieves a particular approved HTTPS
resource under its separate destination policy. Adding `http_get` to shared
selection would require an additional dispatch path and a deliberate user-facing
decision about retrieval authority. The current two-choice path already meets
this feature's bounded selection outcome, so this checkpoint retains it.
Search results are not automatically fetched, and search-to-fetch chaining is
not supported. Use `http-get` explicitly when retrieval is intended.

No registry, common executor interface, keyword router, ranking service,
fallback planner, or autonomous loop is needed to validate and dispatch two
concrete request types. Revisit this decision when a demonstrated task needs
another shared choice or selection-quality measurements reveal a concrete
failure that the current guidance cannot address. Shared metadata remains
Feature [008.05](https://github.com/karanbabu2110/KAOS/issues/896).

## Privacy, safety, and operating limits

Selection sends the prompt and applicable history to configured Ollama with
tool schemas, not private tool configuration values. Approved file contents
reach the model; an approved search query reaches configured SearXNG and its
upstream engines. Tool results are untrusted data. The instruction to ignore
their commands is guidance, while rejecting subsequent tool calls is an
enforced execution boundary. Answer correctness and resistance to misleading
source content are not guaranteed by that boundary.

Existing tool-specific diagnostics distinguish configuration and execution
failures. Invalid model responses return the existing Ollama failure category.
The read-only `tools` catalog can help inspect configuration syntax but does
not establish service reachability or influence the model's choice.

## Repeatable evidence

The existing deterministic tests exercise real application dispatch and the
Ollama HTTP protocol against local fixtures; no live model or public service
is required:

```powershell
./gradlew.bat test --tests io.kaos.app.WebSearchIntegrationTest --tests io.kaos.ai.ollama.OllamaPromptClientTest --no-daemon --console=plain
```

`WebSearchIntegrationTest` proves both selected-tool paths, a direct answer
without tool configuration, exact advertised names, unavailable search,
denied/malformed/EOF approval, rejected unadvertised HTTP calls and multiple
calls, continuation rejection, and conversation persistence boundaries.
`OllamaPromptClientTest` covers protocol decoding and explicit command tool
sets. These tests prove orchestration with predetermined model responses;
they do not measure how reliably a real model chooses the right source.

Validation on 2026-09-09 passed all 47 tests (36 protocol tests and 11
application integration tests), with zero failures, errors, or skips. Production
code and tests are unchanged in this checkpoint; no full packaging validation
was needed for the documentation change.

For manual use, run `tools` to inspect configuration, then use `web-search`
with a project-file question or current-public-facts question. Inspect the
proposed path or query before approving. A direct answer is also a permitted
outcome. See the [developer guide](../development/developer-guide.md) for
configuration and command examples.

This decision is ready for review. The next ordered feature after merge is
[008.05 - Shared Tool Metadata](https://github.com/karanbabu2110/KAOS/issues/896).
