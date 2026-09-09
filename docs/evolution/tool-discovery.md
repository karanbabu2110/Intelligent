# Bounded Tool Discovery

Feature [008.03](https://github.com/karanbabu2110/KAOS/issues/894)
defines tool discovery at KAOS's current scale. It records and verifies existing
runtime behavior rather than adding a registry or dynamic loading mechanism.

## User-visible outcome

When KAOS asks the configured local Ollama model to choose a tool, the request's
standard `tools` field contains the exact semantic capabilities allowed for that
interaction. The model can discover their names, descriptions, and bounded JSON
argument schemas before answering or proposing one call.

Discovery is information, not authority. Advertising a schema does not validate
arguments, approve execution, read a file, contact SearXNG, or bypass any tool's
policy. Configuration and execution dependencies continue to be loaded only
after the selected concrete request reaches its established boundary.

## Current advertised sets

| KAOS operation | Definitions advertised to Ollama | Reason |
| --- | --- | --- |
| `ollama-prompt`, ordinary knowledge operations | None | These are not tool-selection operations |
| `read-local-file` | `read_local_file` | Explicit single-capability command |
| `http-get` | `http_get` | Explicit retrieval command with its distinct network policy |
| `web-search` initial request | `read_local_file`, then `web_search` | The model may select one relevant local or public-information source |
| `conversation` initial request | `read_local_file`, then `web_search` | Same bounded shared selection with retained clean history |
| Any successful tool-result continuation | None | A second tool call is not permitted |

The order is deterministic and each definition occurs once. `http_get` remains
outside shared selection because it retrieves a model-selected page rather than
discovering search results, and it has a distinct destination policy. Adding it
silently would broaden network authority and would pre-empt Feature 008.04's
selection decision.

## Ownership and implementation decision

Each concrete package owns its immutable model definition:

- `io.kaos.tool.readlocalfile.ReadLocalFileToolContract`
- `io.kaos.tool.httpget.HttpGetToolContract`
- `io.kaos.tool.websearch.WebSearchToolContract`

`OllamaPromptClient` explicitly assembles the definitions appropriate to its
concrete request method. `LocalToolsCommand` asks for the bounded two-tool set.
No tool discovers or registers itself, and application startup does not scan
packages, classes, files, services, or remote catalogs.

That direct compile-time enumeration is preferable now because there are only
three concrete tools, their command availability differs, and their schemas do
not share a lifecycle or permission policy. A registry would move ownership out
of the concrete packages without eliminating a demonstrated source of drift.

## Configuration, privacy, and failure behavior

Discovery does not test whether a configured read root exists or whether
SearXNG is running. It does not expose configured roots, URLs, credentials, or
service status to Ollama. A selected tool still performs its own lazy
configuration validation and requests exact user approval before crossing its
protected boundary.

Malformed, unknown, unadvertised, duplicate, or multiple tool calls are invalid
model responses. They execute nothing. A direct textual answer remains valid
when the model selects no tool. A continuation cannot discover another tool
because KAOS omits the `tools` field and rejects a returned call.

There is no new state, persistence, audit content, network call, retry,
cancellation path, startup dependency, or user data introduced by this feature.

## Deterministic evidence

Run:

```powershell
./gradlew.bat test --tests 'io.kaos.app.WebSearchIntegrationTest' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon --console=plain
```

On 2026-09-09 this passed 47 tests with zero failures, errors, or skips. The
loopback tests prove:

- ordinary prompt requests omit tools;
- explicit file and HTTP commands advertise exactly their one definition;
- shared selection advertises exactly file then search;
- direct answers work without loading either selected tool's configuration;
- `http_get` is rejected when it was not advertised;
- multiple, unknown, malformed, and mixed answer/tool responses are rejected;
- both current shared tools can be selected through their concrete path; and
- result continuations advertise no tools and reject chaining.

No live model, filesystem read, SearXNG request, or public-network request is
needed for this evidence.

## Deliberate exclusions and evolution trigger

This feature does not add a `ToolRegistry`, common tool interface, generic
metadata type, plugin manager, `ServiceLoader`, reflection/classpath scan,
configuration-driven loader, remote catalog, marketplace, hot reload, arbitrary
tool installation, or a `tools` listing command. Shared metadata, permission
policy, and execution history remain separate later hypotheses.

Reconsider a registry only when a supported runtime-installed tool exists or
manual compile-time enumeration causes observed drift across real consumers.
Until then, adding a tool means adding its concrete package and explicitly
choosing which request paths may advertise it.

The next ordered feature is
[008.04 - Tool Selection](https://github.com/karanbabu2110/KAOS/issues/895).
It should decide whether current model selection plus the direct dispatcher is
already sufficient before changing routing or introducing shared types.
