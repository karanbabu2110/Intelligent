# Verified web research: first executable use case

Feature [019.01 / #1095](https://github.com/karanbabu2110/KAOS/issues/1095)
belongs to [Epic 019 / #1094](https://github.com/karanbabu2110/KAOS/issues/1094)
under [roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814).
This implements a first end-to-end workflow, not the entire epic.

Follow-up [019.04 / #1096](https://github.com/karanbabu2110/KAOS/issues/1096)
completes partial retrieval and explicit search-only outcomes. It advances this
bounded retrieval improvement before candidate-selection work because publisher
failures otherwise discard useful approved evidence.

Feature [019.09 / #1097](https://github.com/karanbabu2110/KAOS/issues/1097)
adds user-requested persistent exact-host approval for research. This explicitly
replaces research's original non-persistent exact-set approval contract; concrete
HTTP request grants remain single-use. Standalone HTTP and search approvals are unchanged.

Feature [019.11 / #1099](https://github.com/karanbabu2110/KAOS/issues/1099)
adds the research-only, one-use Chromium fallback described here. It revises the
epic's original JavaScript exclusion for this bounded evidence path; general public
browser automation remains excluded.

## Actual behavior

`kaos research "question"` uses the exact question as one approved SearXNG query.
It must satisfy the existing 400-code-point query contract; there is no silent
query truncation or model rewrite. Configure the local Ollama model, SearXNG
endpoint as shown in the [README](../../README.md).

1. Approve the displayed search query. SearXNG contributes at most five titles,
   URLs and snippets. They are discovery data; their URLs are not fetched yet.
2. One structured local-model call proposes one to three result numbers with a
   purpose, suitability reason and provisional PRIMARY, SECONDARY or ANECDOTAL
   role. The application rejects extra fields, invented result numbers, duplicate
   selections, malformed output and duplicate normalized URLs. Only selected
   returned URLs are considered; an unsafe selection stops the run rather than
   silently substituting another source.
3. Application code validates HTTPS syntax and displays normalized URLs and purposes.
   It checks the local approval file and lists previously unapproved exact hosts.
   Type `approve` to persist permission for current and future research reads of
   any HTTPS page on those hosts. Other responses, EOF or cancellation stop the
   operation without saving new hosts or fetching pages. Saved hosts skip this
   prompt. Subdomains are separate; access approval is not a factual trust rating.
4. Each source gets independent shared `http_get` validation and a consumed
   concrete grant. Every DNS answer must be public. One validated address is
   connected directly, while TLS checks the original hostname. No redirects,
   alternate-address attempts, automatic retries, proxy, cookies or credentials
   are used. A source retrieval failure records its failed outcome in content-free
   history and prints its source number and fixed reason. Remaining approved sources
   are processed once, with no replacement URLs. Timeout/unavailable transport
   failures can retry twice with short backoff.
5. A redirect, HTTP 401/403, unavailable or oversized direct response, invalid direct or UTF-8 response, or HTML result with fewer
   than 200 readable code points can offer a separate browser operation. The prompt
   displays the exact URL and host and states that JavaScript will run. Saved host
   approval does not transfer. Denial skips that source without retry. Invalid or
   unsafe URLs, public-address rejection, timeout, rate limiting, cancellation
   and interruption do not offer the fallback.
   Each approval creates a fresh non-persistent Chromium context. Only the exact
   source origin's GET document, script and stylesheet resources can be fetched.
   KAOS checks the selected page's public DNS destination before navigation,
   then fetches and fulfills resources through its own independently validated,
   pinned transport;
   Chromium has no publisher network path.
6. After retrieval, if at least one page succeeded, one structured local-model call
   receives the pages as untrusted tool-result messages. Search snippets are
   absent. It receives no tools. Returned source numbers must reference those
   pages; the application renders citations from the frozen URLs. Claims carry
   FACT, INFERENCE, OPINION, ANECDOTE or CONTRADICTION labels and an uncertainty
   statement. Empty claims mean insufficient evidence and no final answer.
7. If no page succeeded, the same final model-call slot receives only the selected
   search titles, URLs and snippets. Output is labeled `Search-only outcome`, cannot
   contain `FACT`, and must say that pages were not retrieved. These citations point
   to discovery results, not page evidence. Empty claims still produce no answer.

Research also rejects model input that exceeds a conservative byte-based allowance
within the configured context window, reserving output tokens and 1024 units for
framing. It never truncates pages to fit. This is not an exact tokenizer or proof
of provider context retention. With the default 8192-token context, the effective
accepted evidence can be much smaller than the network body caps. A
`MODEL_LOCAL_LIMIT_REACHED` result requires a new run with a suitable explicitly
configured context or smaller sources; it never falls back to snippets.

The program cannot verify whether a page actually supports a claim. Selection
reasons and roles are model judgments. A FACT supported only by ANECDOTAL
sources is rejected; a FACT without a proposed PRIMARY source needs references
from at least two distinct hosts. Distinct hosts are **not proof of independent
publishers or corroboration**. Publisher classification, factual entailment,
freshness and whether the model identifies disagreement correctly remain
limitations. No source is declared universally trusted.

## Bounds and stopping conditions

| Boundary | Enforced value |
| --- | --- |
| Search | One approved SearXNG operation; existing 15-second request bound; at most five results |
| Source set | One to three distinct normalized URLs; one prompt for new exact hostnames |
| Source access | HTTPS; implicit port or 443; persisted approved hostname; all DNS answers public |
| Raw source body | At most 524,288 UTF-8 bytes; `Content-Length` is checked and streaming reads stop at the bound |
| Model source text | At most 65,536 UTF-8 bytes/code points after HTML extraction; raw HTML is never sent |
| Total direct network bodies | At most 4,718,592 bytes through three sources and at most three attempts per source; only successful extracted text enters the model |
| Source attempt | 2-second connect limit; 15-second deadline including DNS, headers and complete body |
| Aggregate direct attempts | At most nine sequential 15-second deadlines, hence 135 seconds of direct retrieval waits; retries apply only to timeout/unavailable failures |
| Response metadata | At most 32 headers, 4096 characters per HTTP protocol line |
| Content | Status 200; plain text, HTML, JSON or XML; strict UTF-8; nonblank; no unsafe control characters or compressed content |
| Selection output | At most three entries; purpose 256 and reason 512 code points |
| Answer output | At most eight claims, 1024 code points each; uncertainty at most 1024; one to three valid source references per claim |
| Model operations | One selection and one page or search-only synthesis; existing local request/token/stream timeouts; no model retries or tools; transient HTTP retries are bounded to two |
| Browser eligibility | Direct redirect, HTTP 401/403, unavailability, oversized, invalid or non-UTF-8 content, or successful HTML with fewer than 200 readable code points |
| Browser approval | One explicit decision for one exact URL/host; never saved or inherited from direct HTTP approval |
| Browser origin/resources | Selected HTTPS origin only; GET document, script and stylesheet; all other schemes, origins, methods and resource types blocked |
| Browser requests/redirects | At most 32 attempted requests and three same-origin redirects per render |
| Browser response data | At most 1,048,576 bytes per resource and 2,097,152 bytes across one research render; compressed bodies rejected. Browser search retains its 524,288-byte per-resource and 1,048,576-byte total limits |
| Browser timing | Public-DNS preflight at most three seconds, 2-second connect, 10 seconds per resource, 20 seconds total including preflight, launch/navigation/render/extraction checks |
| Browser extraction | At most 20,000 text nodes and 65,536 UTF-8 bytes/code points of visible text; title 256 and meta description 512 code points |
| Browser lifecycle | Fresh headless context per approved source; no profile, cookies, credentials, downloads, permissions, retained storage or public control endpoint |

Malformed model output, unexpected tool calls, invalid citations, unsafe URLs,
missing configuration, denial, cancellation, input failure, empty evidence,
history failure or invalid/reused approval stop without a final answer. Recovery requires
a new invocation and search approval. Source-local network, HTTP status, DNS safety,
redirect, content and size failures exclude that source and continue through the
approved set. A partial answer reports successful/approved counts and cites only
successful pages using their original displayed source numbers. Missing primary sources
cannot satisfy the evidence-role guard; a single remaining secondary source is
still insufficient for a FACT claim. If every source fails, search-only synthesis
uses the already approved search data and rejects `FACT`; it never presents snippets
as retrieved pages. Failure output prints a content-free `ERROR` with source number
and fixed reason plus the successful retrieval count; it never prints raw page or
failure-body content. Exact selected URLs remain visible in the proposal, browser
approval prompt and successful source attribution.

Direct bodies remain bounded textual responses; their HTML scripts are never
executed. The separately approved fallback executes same-origin JavaScript in a
fresh Chromium context and extracts bounded visible text, page title and meta
description. Browser-rendered evidence
is encoded as `browser_rendered`, is called untrusted evidence in the synthesis
instruction, and is printed as `BROWSER-RENDERED`; direct evidence is printed as
`DIRECT-HTTP`. Scripts cannot change the frozen source set, grant authority, add a
tool call or escape deterministic request limits. JSON/XML direct responses are not
parsed for external entities. Model prose cannot contain URLs or bracketed
citations; the application supplies source references.

## Architecture decision from current code

The inspected canonical main was `f8b64ba`. `AgentPlan` permits three total steps
and two tool steps, accepts only fixed local/search evidence shapes, and binds
requests before execution. `AgentExecutor` independently restricts tool names to
the local-file/search scope. Search followed by three GETs cannot fit those
contracts. Calling this four-operation sequence a two-tool agent plan would hide
authority and execution accounting.

The feature therefore uses a focused application command with a fixed sequence.
It reuses `AgentGoal` for objective validation and the same `ToolRegistry`,
`KaosTool`, `ToolSelector`, concrete policies/grants and content-free history.
It deliberately leaves `AgentPlan`, `AgentPlanner`, `AgentExecution` and
`AgentExecutor` unchanged rather than manufacturing an agent completion state.
The Ollama client shares its bounded no-tools evidence serializer, but research
has a separate evidence-shape check; agent evidence limits are not widened.

Search normalization, SearXNG configuration, permission policy, exact grants,
registry and tool-history storage are reused unchanged. The shared HTTP path
needed these fixes before research could safely reuse it:

- Percent escapes were being escaped again during URL normalization.
- DNS checks were separate from the HTTP client's connection lookup.
- Default socket behavior could inherit HTTP/SOCKS proxy settings.
- A header timeout did not bound the subsequent streamed body read.
- All `text/*` media were accepted, exceeding this epic's media boundary.

Apache HttpClient 5.6.4 supplies the maintained HTTP parser, TLS and custom DNS
connection boundary. The application still owns URL validation, public-address
policy, exact grants, content validation and deadlines. No module, framework,
trust database, crawler or generalized workflow abstraction was added.

### Browser deployment decision

This slice keeps Playwright 1.63.0 and Chromium inside the single KAOS process.
The existing dependency and explicit browser installation already support local
Chromium lifecycle, while every publisher byte still crosses the application-owned
Apache transport. Chromium context routes receive only fulfilled bounded bytes or
an abort, so moving Chromium into Docker would not replace the URL, DNS, redirect,
origin or byte policies. A new worker would also require a new authenticated control
protocol and service lifecycle without current reuse or deployment evidence.

No browser worker, Compose service, published port or CDP endpoint is added. If a
later deployment requires operating-system isolation, the remaining step is a
dedicated non-root worker on an internal service network, with no published control
port, no host profile mounts, no privilege escalation and an image pinned to the
repository's Playwright Java 1.63.0 version. That worker must retain the same
application request policy; Docker is defense in depth rather than an SSRF or DNS
rebinding boundary. The current in-process residual risk is that a Chromium exploit
shares the KAOS user's operating-system authority.

## Privacy and security review

The exact query reaches configured SearXNG only after approval. Its configured
upstream engines may receive the query. Each approved publisher sees the URL
path/query and ordinary connection metadata. The selected search results and
retrieved text reach configured local Ollama. Nothing in this command loads
conversation history, explicit memory, browser profiles or credential stores.
Each approved browser fallback creates and closes a new context. It grants no
permissions, accepts no downloads, forwards no browser cookies or authorization
headers, retains no `Set-Cookie` response, and exposes no remote-control endpoint.

Research suppresses existing payload debug tracing. The application-start trace
redacts research arguments. Tool history receives only operation identifiers,
tool names, decisions, outcomes and timestamps; it stores no query, URL, content,
source reason or answer. Other commands retain their existing debug behavior.
The separate research approval file persists only exact hostnames. It is bounded
to 256 hosts and 64 KiB of strict UTF-8, uses locked atomic replacement and fails
closed on invalid or unavailable storage. Deleting a line revokes that host;
deleting the file resets approvals. It is checked at startup and before each
page attempt; it cannot cancel an already running request. Local users able to
edit this file can change research access authority. See the developer guide
for the path override and storage recovery.
The terminal displays exact URLs, purposes and the final answer. Shell history
and the configured model/search services have their own retention behavior.

The shared URL validator also rejects recognized credential-bearing query keys
(including token, password, secret, credential, signature, API-key and session
forms, after percent-decoding parameter names). This conservative rule can reject
benign token-like parameters. Arbitrary server-specific credential conventions
cannot be inferred from a URL; review exact URLs and purposes before approval.

The shared HTTP transport rejects raw IPs, private/local/multicast/unspecified
addresses, reserved IPv4 ranges and nonordinary IPv6 space. Requiring every
answer to pass blocks mixed public/private DNS responses. Only one checked
address reaches the socket; direct HTTP redirect responses are aborted. The browser
fallback instead validates and follows at most three same-origin redirects through
the same pinned Java transport. Hostname verification and platform trust roots
remain enabled. TLS does not prove factual correctness.

On timeout/cancellation, the request and connection are aborted and the worker
is interrupted. An OS resolver may finish an uninterruptible lookup later;
post-resolution cancellation checks prevent it from starting a source request.
This is not a persistent background retrieval task. HTTP dependency updates need
the pinning, proxy, deadline and TLS regression tests again.

Prompt injection can still influence the model's wording or judgment. It cannot
change the frozen source set, authorize a grant, request another model/tool call,
increase limits or initiate an operation. Tests demonstrate mechanical containment,
not semantic immunity or factual correctness.

## Repeatable local evidence

```powershell
./gradlew.bat test --tests 'io.kaos.app.ResearchCommandTest' --tests 'io.kaos.app.research.*' --tests 'io.kaos.tool.browserrender.*' --tests 'io.kaos.tool.httpget.*' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
git diff --check
```

`ResearchCommandTest` runs controlled search and source HTTP servers and a
loopback Ollama protocol fixture. It demonstrates the three-source path,
snippet/page separation, exact normalized approval, denial/EOF, invalid source
selection, partial failure, injection containment, disagreement, missing evidence,
anecdotal labeling and content-free history/debug behavior. No installed Ollama
or public internet is needed. The HTTP fixture mapping is test-only; production
configuration cannot enable local/non-HTTPS source access.

`ResearchBrowserRendererTest` launches the pinned local Chromium against fulfilled
fixture responses. It proves JavaScript extraction, fresh storage, exact-origin
script/style access, blocked third-party/API/image/WebSocket requests, redirects,
size/text/time limits and cleanup after failure. `ResearchBrowserRequestPolicyTest`
covers methods, navigation, form-style POST rejection, redirect and request counts.
`PinnedBrowserResourceFetcherTest` uses only a loopback fixture to prove address
pinning, no credential/proxy inheritance, byte/deadline enforcement and no implicit
redirect or retry. Ollama serialization tests prove rendered evidence advertises no
tools and retains its evidence type.

`PinnedHttpTransportTest` exercises real response bodies, no-proxy behavior,
single DNS binding, redirect/non-200 handling, media/size/empty rejection,
deadlines, late DNS completion after cancellation, URL normalization, grant/request
binding, compressed/oversized metadata rejection and non-public DNS denial. Existing
HTTP contracts continue to test exact grants and malformed UTF-8.

An optional live demonstration uses the README command. Review the search prompt,
any new-host prompt and each browser-render prompt. Redirects and non-UTF-8 direct
responses and oversized pages may offer the bounded browser fallback.
Invalid approval storage or model schema failures stop the run.
The separate [live evaluation](verified-web-research-live-evaluation.md) records
local-model/public-source runs and the quality and safety problems they exposed.
The deterministic tests alone do not demonstrate public-source factual accuracy.

### Live follow-up: 2026-09-29

The following results predate persistent host approvals.

With local SearXNG at `127.0.0.1:8080`, `qwen3.5:4b`, thinking off and
the default context settings, two runs of `latest stock market news` stopped
at `DISALLOWED_HOST` before retrieval. A separate search inspection did not
guarantee that its result hosts would match the next application search.

A scoped run, `site:reuters.com/markets/ latest stock market news`, with
`www.reuters.com,reuters.com` allowed, selected `/markets/`, `/markets/us/`
and `/markets/news/` on `www.reuters.com`. After exact-set approval, all three
returned `HTTP_UNAUTHORIZED`. KAOS attempted each once and completed a labeled
`Search-only outcome` with `SEARCH RESULT ONLY` citations and an explicit
no-pages-retrieved uncertainty statement. Gradle reported success in 38 seconds.
This demonstrates the live all-failed continuation, not successful page retrieval
or the mixed-success path (which remains covered by deterministic fixtures).

The generated prose still contained factual-sounding market claims labeled as
OPINION and called a market contrast CONTRADICTION. Neither freshness nor factual
support was established. Rejecting the FACT label and displaying the fallback
warning does not enforce semantic claim quality. The cause of the publisher's
401 responses was not established by this run.

### Persistent-host live check: 2026-09-29

Two separate Gradle/Java processes used local SearXNG, `qwen3.5:4b`, thinking off,
the same Reuters-scoped query and an isolated temporary approval file, with
`KAOS_HTTP_ALLOWED_HOSTS` unset. The first process displayed `www.reuters.com`,
accepted explicit approval, and saved only that hostname. It completed in 43 seconds.
The second process received only search approval, displayed `Using saved publisher
host approvals; no new page approval required.`, and completed in 26 seconds.
Both runs received publisher 401 responses and produced labeled search-only output.
This proves persisted host authorization across process restarts, not authenticated
access or factual accuracy. Default user approvals were not modified by this test.
Deterministic tests separately prove successful page reads after reload, changed
paths on the same host, revocation, exact-host boundaries and storage failure handling.

## Roadmap and next slice

No active duplicate research feature or child of #1094 existed at inspection.
#1095 was created and linked as the first child. #814 links Epic 019 but its
inventory still says 19 epics and omits 019. The stale inventory is recorded here;
this feature does not revise unrelated roadmap scope.

The smallest next feature is 019.02, Candidate Source Selection: improve selection
of suitable primary sources and explain exclusions before approval, without
retries or extra retrieval. Robust publisher
independence checks, passage-level attribution and semantic evidence validation
remain unimplemented. The epic stays open.
