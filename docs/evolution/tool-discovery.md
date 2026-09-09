# Bounded Tool Discovery

Feature [008.03](https://github.com/karanbabu2110/KAOS/issues/894)
defines tool discovery at KAOS's current scale. It adds one read-only `tools`
catalog command and records existing model-facing behavior without adding a
registry or dynamic loading mechanism.

## User-visible catalog

Run:

```powershell
./gradlew.bat --console=plain run --args=tools
```

The stable output shape is:

```text
KAOS tool catalog:
read_local_file | Read one bounded local file | approval: required | configuration: <configured|unavailable>
http_get | Retrieve one allowed HTTPS resource | approval: required | configuration: <configured|unavailable>
web_search | Discover public URLs through SearXNG | approval: required | configuration: <configured|unavailable>
Configuration status is local-only; no file, DNS, service, or web request was made.
Every execution still requires exact validation and approval.
```

The command succeeds even when every tool is unavailable. `configured` means
the applicable local environment/JVM setting exists and passes its current
syntax validation. It does not mean a file exists, a hostname resolves, Ollama
supports the tool, or SearXNG/upstream engines are reachable. `unavailable`
combines absent and invalid configuration without exposing the private value.

The command performs no model call, target validation, DNS lookup, service
probe, web request, approval, or execution. It never prints configured roots,
hosts, or endpoints. The catalog is a command-owned fixed presentation, not an
executor map.

## Model-visible outcome

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
| `tools` | No model request | User-facing catalog only |
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

`ToolCatalogCommand` owns the fixed user-facing list and configuration-status
presentation. It does not supply definitions to `OllamaPromptClient` or map
names to `LocalToolsCommand` executors. The catalog and model definitions remain
explicit at their two distinct consumers. Feature 008.05 should introduce shared
metadata only if demonstrated drift makes that duplication costly.

That direct compile-time enumeration is preferable now because there are only
three concrete tools, their command availability differs, and their schemas do
not share a lifecycle or permission policy. A registry would move ownership out
of the concrete packages without eliminating a demonstrated source of drift.

## Configuration, privacy, and failure behavior

Model discovery does not test whether a configured read root exists or whether
SearXNG is running. It does not expose configured roots, URLs, credentials, or
service status to Ollama. A selected tool still performs its own lazy
configuration validation and requests exact user approval before crossing its
protected boundary.

Malformed, unknown, unadvertised, duplicate, or multiple tool calls are invalid
model responses. They execute nothing. A direct textual answer remains valid
when the model selects no tool. A continuation cannot discover another tool
because KAOS omits the `tools` field and rejects a returned call.

The user catalog catches configuration-loader failures and reports only
`unavailable`; it does not retain exception details. There is no new state,
persistence, audit content, network call, retry, cancellation path, startup
dependency, or user data introduced by this feature.

## Deterministic evidence

Run:

```powershell
./gradlew.bat test --tests 'io.kaos.app.ToolCatalogCommandTest' --tests 'io.kaos.app.CommandRouterTest' --tests 'io.kaos.app.KaosApplicationTest' --tests 'io.kaos.app.KaosApplicationProcessTest' --no-daemon --console=plain
./gradlew.bat test --tests 'io.kaos.app.WebSearchIntegrationTest' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon --console=plain
```

The model-discovery selection passed 47 tests before catalog implementation.
After implementation, the focused catalog, routing, help, and real-process
selection passed 83 tests with zero failures, errors, or skips. The public
`tools` command also completed successfully through the Gradle application
runner. Together this deterministic evidence proves:

- ordinary prompt requests omit tools;
- the catalog has stable order, reports configured/unavailable state, and never
  prints injected private configuration values;
- `tools` accepts no arguments and invokes only its catalog handler;
- explicit file and HTTP commands advertise exactly their one definition;
- shared selection advertises exactly file then search;
- direct answers work without loading either selected tool's configuration;
- `http_get` is rejected when it was not advertised;
- multiple, unknown, malformed, and mixed answer/tool responses are rejected;
- both current shared tools can be selected through their concrete path; and
- result continuations advertise no tools and reject chaining.

No live model, filesystem read, SearXNG request, or public-network request is
needed for this evidence.

Final `clean verifyLocal --no-daemon --warning-mode=all --console=plain` passed
all 11 tasks: compile, 418 tests (414 passed and four existing Windows symbolic-
link skips), packaging, status, and help. The exact final tree added three
catalog tests and one router test with no failures or errors.

## Deliberate exclusions and evolution trigger

This feature does not add a `ToolRegistry`, common tool interface, generic
metadata type, plugin manager, `ServiceLoader`, reflection/classpath scan,
configuration-driven loader, remote catalog, marketplace, hot reload, or arbitrary
tool installation. Shared metadata, permission
policy, and execution history remain separate later hypotheses.

Reconsider a registry only when a supported runtime-installed tool exists or
manual compile-time enumeration causes observed drift across real consumers.
Until then, adding a tool means adding its concrete package and explicitly
choosing which request paths may advertise it.

The next ordered feature is
[008.04 - Tool Selection](https://github.com/karanbabu2110/KAOS/issues/895).
The [selection decision](tool-selection.md) retains current model selection
and direct dispatch, with explicit limits on source quality and HTTP retrieval.
