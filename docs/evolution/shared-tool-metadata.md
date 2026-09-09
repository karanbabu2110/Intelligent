# Shared Tool Metadata

Feature [008.05](https://github.com/karanbabu2110/KAOS/issues/896), under
[Epic 008](https://github.com/karanbabu2110/KAOS/issues/63) and roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814), removes duplicated
tool identities from the user catalog and defines the current metadata owners.

## Concrete outcome

`ToolCatalogCommand` now reads the existing `NAME` constant from each concrete
tool contract. The same constants already identify the model definitions and
response decoding in `OllamaPromptClient`. The catalog previously repeated all
three literal names. They were consistent at this checkpoint; this change
eliminates the extra edit location without claiming an observed naming defect.

The `tools` output remains unchanged: file, HTTP retrieval, and search in that
order, with short purposes, approval requirements, and local configuration
status. Run it with:

```powershell
./gradlew.bat --console=plain run --args=tools
```

## Ownership decision

| Metadata | Owner and consumers |
| --- | --- |
| Stable tool name | Each concrete `*ToolContract.NAME`; consumed by model definitions, decoding, and the catalog |
| Model description and argument schema | Concrete tool contract; consumed by Ollama request construction |
| Short human-facing purpose | `ToolCatalogCommand`; consumed by terminal users |
| Catalog order and available entries | `ToolCatalogCommand`; explicit fixed presentation |
| Model-advertised tool set | `OllamaPromptClient`; selected per operation |
| Configuration readiness | Existing concrete configuration loaders, rendered by the catalog |
| Execution permission | Concrete validators and approvals; never granted by metadata |

The catalog purposes deliberately differ from model instructions. For example,
the short search purpose names SearXNG, while the model description explains
that search returns titles, URLs, and snippets without fetching pages. Combining
these strings would change an established output or prompt without a need.
The three JSON schemas also retain their concrete argument names and bounds.

The proven common metadata is the immutable tool name, already represented by
a constant. A shared record, interface, JSON wrapper, or registry would add a
new owner without removing another demonstrated problem. Reconsider a shared
type when multiple current consumers need the same additional fields and
independent copies cause actual drift. No module or service boundary is needed.

## Lifecycle, safety, and limits

Names are compile-time constants. The catalog does not build model schemas,
discover executors, load plugins, or change startup dependencies. It continues
to inspect local configuration syntax only, never a target file or remote
service. Configuration failures remain `unavailable`, unexpected programming
failures propagate, and private configuration values are not printed.

No new input, state, persistence, cancellation, retry, permission, or network
path is introduced. The literal `approval: required` describes every current
tool but does not enforce approval. Actual execution still uses the existing
concrete policy. Names do not authorize tools or change the advertised sets
recorded in [tool discovery](tool-discovery.md) and
[tool selection](tool-selection.md).

Tool identity changes remain compatibility decisions: sharing a constant does
not make renaming a public tool safe. Explicit catalog enumeration and direct
dispatch remain separate consumers with different responsibilities.

## Verification and next checkpoint

Existing tests exercise stable catalog names/order, safe configured and
unavailable states, configuration redaction, and unexpected failures. Protocol
and integration tests verify the model-visible names and allowed tool sets:

```powershell
./gradlew.bat test --tests io.kaos.app.ToolCatalogCommandTest --tests io.kaos.ai.ollama.OllamaPromptClientTest --tests io.kaos.app.WebSearchIntegrationTest --no-daemon --console=plain
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all --console=plain
```

The focused selection passed all 50 tests on 2026-09-09 with no failures,
errors, or skips. Full `clean verifyLocal` passed all 11 tasks with 418 tests:
414 passed, zero failures or errors, and four existing Windows symlink skips.

README retains its existing product description of three concrete tools and a
fixed read-only catalog. The completed-work index corrects its stale Epic 008
checkpoint, and the [developer guide](../development/developer-guide.md) links
this ownership record. No test is added solely to mirror constant references.

After this feature is merged, the next ordered feature is
[008.06 - Tool Permission Policies](https://github.com/karanbabu2110/KAOS/issues/897).
