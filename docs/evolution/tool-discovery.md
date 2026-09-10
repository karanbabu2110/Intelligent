# Bounded Tool Discovery

Feature [008.03](https://github.com/karanbabu2110/KAOS/issues/894)
defines tool discovery at KAOS's current scale. It adds one read-only `tools`
catalog command. The follow-up for Features 008.04-008.06 now supplies its
metadata through a shared in-memory registry. Dynamic loading is not implemented.

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
hosts, or endpoints. The catalog iterates registry descriptors and local-only
configuration status, without preparing a request or using execution authority.

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
silently would broaden network authority beyond the current selection contract.

## Ownership and implementation decision

Each concrete package owns its immutable model definition:

- `io.kaos.tool.readlocalfile.ReadLocalFileToolContract`
- `io.kaos.tool.httpget.HttpGetToolContract`
- `io.kaos.tool.websearch.WebSearchToolContract`

`StandardTools` constructs the three adapters in deterministic order and defines
explicit immutable operation scopes. `ToolRegistry` accepts constructor-supplied
tools, rejects duplicate names, and exposes read-only lookup and ordered lists.
No tool discovers or registers itself. Startup does not scan packages, classes,
files, services, or remote catalogs, and does not read tool configuration.

`ToolCatalogCommand` consumes registry descriptors for stable names, human-facing
purposes, and approval indications. Each adapter supplies configuration readiness
using its existing local loader. Model descriptions and schemas remain concrete
contract data. `ToolSelector` derives only the definitions permitted by its
caller's explicit scope; `OllamaPromptClient` uses it for both advertisement and
response selection. Registry membership grants neither advertisement nor
execution authority. See [shared metadata](shared-tool-metadata.md) and
[selection](tool-selection.md) for the runtime boundaries.

## Configuration, privacy, and failure behavior

Model discovery does not test whether a configured read root exists or whether
SearXNG is running. It does not expose configured roots, URLs, credentials, or
service status to Ollama. A selected tool still performs its own lazy
configuration validation and requests exact user approval before crossing its
protected boundary.

Malformed, unknown, unadvertised, duplicate, or multiple tool calls are invalid
model responses, with safe selection failure categories where applicable. They execute nothing. A direct textual answer remains valid
when the model selects no tool. A continuation cannot discover another tool
because KAOS omits the `tools` field and rejects a returned call.

Concrete adapters classify expected configuration-loader failures and report only
`unavailable`; the catalog does not retain exception details. There is no new state,
persistence, audit content, network call, retry, cancellation path, startup
dependency, or user data introduced by this feature.

## Historical discovery evidence

Run:

```powershell
./gradlew.bat test --tests 'io.kaos.app.ToolCatalogCommandTest' --tests 'io.kaos.app.CommandRouterTest' --tests 'io.kaos.app.KaosApplicationTest' --tests 'io.kaos.app.KaosApplicationProcessTest' --no-daemon --console=plain
./gradlew.bat test --tests 'io.kaos.app.WebSearchIntegrationTest' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon --console=plain
```

At the original merged `a9d67ea` checkpoint, the model-discovery selection
passed 47 tests before catalog implementation.
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

## Current extension boundary

This is not a plugin framework. It adds no plugin manager, ServiceLoader,
reflection/classpath scan, dynamic class loading, remote catalog, marketplace,
hot reload, or arbitrary tool installation. Tool installation remains an explicit
code/composition change. Adding a registered tool does not add it to any model
operation until its allowed list is deliberately changed.

The current follow-up adds deterministic registry and descriptor integration
tests, including a fourth fixture tool whose catalog entry requires no command
change. Existing catalog order, redaction, missing configuration, and unexpected
failure tests remain. Current full validation is recorded in
[permission policies](tool-permission-policies.md).

The next ordered feature is
[008.07 - Tool Execution History](https://github.com/karanbabu2110/KAOS/issues/899).
