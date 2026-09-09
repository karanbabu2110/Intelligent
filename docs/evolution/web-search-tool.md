# Web Search through separate SearXNG

Feature [008.02](https://github.com/karanbabu2110/KAOS/issues/893) adds the
KAOS capability `web_search`. SearXNG is separately operated infrastructure:

```text
KAOS Java application -> configured SearXNG service -> upstream search engines
         |
         +-> local Ollama: selection and final answer
```

KAOS remains one Java application. Its `io.kaos.tool.websearch` package owns
query validation, exact approval, a concrete SearxngClient, normalization, and
safe failures. Java's existing HTTP client and Jackson are sufficient; there
is no new dependency, module, registry, wrapper microservice, or embedded
SearXNG code.

## Selection and lifecycle

`conversation` and `web-search "<question>"` advertise `read_local_file` and
`web_search` together. A direct dispatcher accepts no tool or one selected
tool, validates it, requests approval, executes it once, then performs one final
Ollama continuation advertising no tools. Existing explicit `read-local-file`
and `http-get` commands retain their dedicated tool behavior.

The conversation uses one input reader for prompts and approval. Tool-backed
turns, including their user prompt and final answer, are not appended to SQLite
or retained conversation history, because the answer can repeat external or
file content. No-tool conversation turns retain their existing persistence.
The initial selection sees retained no-tool history; a tool continuation uses
the current question, exact tool call, and normalized result only. Users should
make tool questions self-contained. A later turn requires a fresh approval.

## Configuration

| Setting | Value | Precedence |
| --- | --- | --- |
| `KAOS_WEB_SEARCH_SEARXNG_URL` | Explicit HTTP(S) origin, e.g. `http://127.0.0.1:8080`; no default | Environment |
| `kaos.web-search.searxng-url` | Same origin format | JVM property overrides environment, including invalid/empty values |

Only configuration selects the destination. Origins must have a host, optional
valid port, and no credentials, query, fragment, or path prefix. KAOS appends
`/search`. The configured endpoint is trusted operator infrastructure, not a
model-controlled URL. Localhost is recommended; a remotely hosted service
changes the deployment privacy boundary and should use HTTPS.

Configuration is loaded only when search is selected. Missing configuration
does not prevent startup, ordinary Ollama questions, or local file tools.
An absent service produces a bounded failure only after search approval.

## Exact request and result

```json
{"query":"latest stable Spring Boot release"}
```

The request must contain exactly one string query. KAOS preserves it exactly,
rejects null, blank, ISO controls, Unicode format controls and malformed
surrogates, and allows at most 400 Unicode code points. Unknown fields are
rejected. SearXNG bang syntax (`!`) and whitespace-delimited colon directives
are rejected because they can select engines/languages or redirect. Ordinary
search terms such as `site:spring.io` remain available.

The sole HTTP request is `GET /search?q=<UTF-8 encoded exact query>&format=json`.
Engine, language, category and SafeSearch defaults belong to SearXNG deployment
configuration. No headers, endpoint, count, page, or policy arguments are
accepted from the model. There are no redirects, cookies, credentials, proxy,
application retries, or automatic result URL fetches.

| Boundary | Limit |
| --- | --- |
| Connect timeout | 2 seconds |
| Total HTTP completion including body | 15 seconds |
| Raw response | 65,536 bytes |
| JSON nesting | 16 levels |
| Results | First 5 in provider order |
| Title | Required nonblank string, 256 code points |
| URL | Required HTTP(S) URL with host and no credentials, 2,048 code points |
| Snippet | Optional content string, defaults to empty, 512 code points |
| Encoded KAOS result | 16,384 UTF-8 bytes |

SearXNG does not expose a portable result-count parameter. KAOS retains only
the first five results; it neither paginates nor reorders them. Invalid retained
entries fail the whole result. Fields are not silently truncated. Unknown
provider metadata is discarded. Duplicate JSON keys, trailing data, malformed
UTF-8, invalid JSON and unsupported responses fail safely.

```json
{"query":"latest stable Spring Boot release","results":[{"title":"Spring Boot","url":"https://spring.io/projects/spring-boot","snippet":"Release information"}]}
```

Zero results is a successful empty list. URLs are data only. No page HTML,
headers, raw provider JSON, engine details, or debugging metadata are supplied
to Ollama. Search results are untrusted data. They are not tool instructions
and do not receive execution authority.

## Approval, audit, and failure

The approval prompt displays the exact query and explains that configured
SearXNG may forward it to external engines. An approval grants one attempt for
that query only. The executor obtains the query from the consumed grant, not
from a second caller argument. Denial, malformed approval and end-of-input
perform zero search calls and zero final continuations.

Audit reports a random identifier, decision and execution outcome only.
Query/result values and endpoint details are redacted from ordinary object
representations and exceptions. The query is intentionally visible in the local
approval prompt. The user's final answer may naturally repeat result content.
KAOS does not persist raw queries, results or audit records in this path.
SearXNG and upstream logging/retention remain deployment concerns.

Errors use the existing application ErrorReporter and fixed
`KAOS-WEB-SEARCH-*` diagnostics. Concepts include invalid request/configuration,
service not configured/unavailable, timeout, invalid response, oversized result,
cancellation and consumed approval. No raw transport cause or provider body is
retained. Correct the configuration or service and submit a new request to
retry; prior approval cannot be reused.

## Development and verification

See [separate service setup](../development/searxng-setup.md).
Automated tests use ephemeral loopback Ollama and SearXNG stubs, with no
installed model, running SearXNG, Docker or internet requirement.

```powershell
./gradlew.bat test --tests 'io.kaos.tool.websearch.*' --tests 'io.kaos.app.WebSearchIntegrationTest' --no-daemon
./gradlew.bat test --tests 'io.kaos.tool.*' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The meaningful shared requirement is the approval input reader and the
file-or-search dispatcher. Provider interfaces and generic discovery are
unnecessary with this one concrete search implementation. Future discovery or
policy work should use evidence from these concrete paths; it is not part of
this feature.

References: [SearXNG Search API](https://docs.searxng.org/dev/search_api.html)
and [search syntax](https://docs.searxng.org/user/search-syntax.html).

## Feature validation and review inventory

Validated on Windows/Java 21 on 2026-09-09. `clean verifyLocal --no-daemon
--warning-mode=all` passed all 11 Gradle tasks: compilation, tests, packaging,
status, and help. JUnit reported 414 tests: 410 passed, zero failures/errors,
and four existing platform-dependent symlink skips (one knowledge-ingestion
test and three file-validator tests). All 21 new search tests passed.

Focused search/application/process tests also passed. Existing local-file and
Ollama tool tests passed; their tests were not modified. Normal suites use only
loopback stubs. The optional Compose deployment was additionally validated on
Docker Desktop 29.6.2: Compose resolved one SearXNG service, the container became
healthy on loopback, `/healthz` returned 200, `/config` reported `KAOS Local
Search`, and one explicit non-sensitive smoke query returned a JSON results
array. Live model answer quality was not tested or claimed by this evidence.

The tests prove absent configuration is lazy, unavailable service errors are
safe, denial makes zero search/continuation calls, exact approved Unicode query
encoding, no endpoint override, one selected tool, no second-call continuation,
five normalized results, zero result-page fetches, and no tool-turn SQLite
history. A separate no-tool conversation test proves normal history remains
stored and supplied on the next turn. Startup process tests remain unchanged.

Files included in this feature (paths relative to the repository root):

| File | Reason |
| --- | --- |
| `README.md` | Configuration/command tables, usage, and current feature boundaries |
| `docs/development/developer-guide.md` | Multi-tool operation and persistence/setup implications |
| `compose.yaml` | Loopback-only, separately running official SearXNG container |
| `docker/searxng/settings.yml` | Minimal inherited SearXNG policy with JSON enabled |
| `docs/development/searxng-setup.md` | Separate container deployment and troubleshooting |
| `docs/evolution/web-search-tool.md` | Contract, architecture, limits, privacy, and validation evidence |
| `src/main/java/io/kaos/ai/ollama/OllamaPromptClient.java` | Two-tool advertisement, exact decoding, no-tools continuation |
| `src/main/java/io/kaos/ai/ollama/package-info.java` | Updated concrete tool-selection responsibility |
| `src/main/java/io/kaos/app/ApplicationRuntime.java` | Lazy concrete composition without startup search dependency |
| `src/main/java/io/kaos/app/CommandRouter.java` | Explicit web-search command route |
| `src/main/java/io/kaos/app/KaosApplication.java` | Command help |
| `src/main/java/io/kaos/app/ConversationCommand.java` | Shared selection and foreground-only tool turns |
| `src/main/java/io/kaos/app/ReadLocalFileCommand.java` | Reuse existing file approval through the conversation reader |
| `src/main/java/io/kaos/app/ApprovalInput.java` | Bounded approval input without losing buffered prompts |
| `src/main/java/io/kaos/app/LocalToolsCommand.java` | Direct file/search dispatch, approval, audit, and safe errors |
| `src/main/java/io/kaos/tool/websearch/WebSearchRequest.java` | Exact bounded query validation |
| `src/main/java/io/kaos/tool/websearch/TextBounds.java` | Package-local Unicode/control checks |
| `src/main/java/io/kaos/tool/websearch/WebSearchResult.java` | Bounded normalized result values |
| `src/main/java/io/kaos/tool/websearch/WebSearchToolContract.java` | Semantic model schema and bounded result encoding |
| `src/main/java/io/kaos/tool/websearch/WebSearchApproval.java` | Exact single-decision, single-use authority |
| `src/main/java/io/kaos/tool/websearch/WebSearchException.java` | Content-free classified failures |
| `src/main/java/io/kaos/tool/websearch/SearxngClient.java` | Fixed bounded JSON search transport and normalization |
| `src/main/java/io/kaos/tool/websearch/package-info.java` | Separate infrastructure ownership and untrusted-data boundary |
| `src/test/java/io/kaos/tool/websearch/WebSearchContractTest.java` | Query, approval, result and configuration limits |
| `src/test/java/io/kaos/tool/websearch/SearxngClientTest.java` | Deterministic HTTP, normalization, failure and deadline tests |
| `src/test/java/io/kaos/app/WebSearchIntegrationTest.java` | End-to-end selection, approval, continuation and persistence |

Reviewed historical Epic 007 exit, minimal-tool contract, and first-tool
invocation documentation remain unchanged as evidence of their completed
single-tool milestone. No architecture website, release/version file, Java
build dependency, SearXNG source, custom image, or Docker build integration was
changed. The optional Compose file starts SearXNG only.
