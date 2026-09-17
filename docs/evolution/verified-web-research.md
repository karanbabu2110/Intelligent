# Verified web research: first executable use case

Feature [019.01 / #1095](https://github.com/karanbabu2110/KAOS/issues/1095)
belongs to [Epic 019 / #1094](https://github.com/karanbabu2110/KAOS/issues/1094)
under [roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814).
This implements a first end-to-end workflow, not the entire epic.

## Actual behavior

`kaos research "question"` uses the exact question as one approved SearXNG query.
It must satisfy the existing 400-code-point query contract; there is no silent
query truncation or model rewrite. Configure the local Ollama model, SearXNG
endpoint and explicit HTTP allowed hosts as shown in the [README](../../README.md).

1. Approve the displayed search query. SearXNG contributes at most five titles,
   URLs and snippets. They are discovery data; their URLs are not fetched yet.
2. One structured local-model call proposes one to three result numbers with a
   purpose, suitability reason and provisional PRIMARY, SECONDARY or ANECDOTAL
   role. The application rejects extra fields, invented result numbers, duplicate
   selections, malformed output and duplicate normalized URLs. Only selected
   returned URLs are considered; an unsafe selection stops the run rather than
   silently substituting another source.
3. Application code validates HTTPS syntax and exact configured hosts, then
   displays the normalized URLs and purposes. Review all of them. Type `approve`
   once to authorize only that frozen set, one GET per URL. Any other response,
   EOF or cancellation stops the operation. Approval is not stored or reusable.
4. Each source gets independent shared `http_get` validation and a consumed
   concrete grant. Every DNS answer must be public. One validated address is
   connected directly, while TLS checks the original hostname. No redirects,
   alternate-address attempts, automatic retries, proxy, cookies or credentials
   are used. A source failure stops later sources. Output reports completed
   retrievals, and no partial answer is generated.
5. Only after every selected page succeeds, one structured local-model call
   receives the pages as untrusted tool-result messages. Search snippets are
   absent. It receives no tools. Returned source numbers must reference those
   pages; the application renders citations from the frozen URLs. Claims carry
   FACT, INFERENCE, OPINION, ANECDOTE or CONTRADICTION labels and an uncertainty
   statement. Empty claims mean insufficient evidence and no final answer.

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
| Source set | One to three distinct normalized URLs; one approval response for that set |
| Source access | HTTPS; implicit port or 443; exact configured hostname; all DNS answers public |
| Source body | At most 32,768 UTF-8 bytes, hence at most 32,768 Unicode code points; never truncated |
| Total source bodies | At most 98,304 UTF-8 bytes/code points through the three-source bound |
| Source attempt | 2-second connect limit; 15-second deadline including DNS, headers and complete body |
| Aggregate attempts | At most three sequential 15-second deadlines, hence 45 seconds of retrieval waits; user/model/history time is separate |
| Response metadata | At most 32 headers, 4096 characters per HTTP protocol line |
| Content | Status 200; plain text, HTML, JSON or XML; strict UTF-8; nonblank; no unsafe control characters or compressed content |
| Selection output | At most three entries; purpose 256 and reason 512 code points |
| Answer output | At most eight claims, 1024 code points each; uncertainty at most 1024; one to three valid source references per claim |
| Model operations | One selection and one synthesis; existing local request/token/stream timeouts; no retries or tools |

Malformed model output, unexpected tool calls, invalid citations, unsafe URLs,
missing configuration, denial, cancellation, read failure, empty evidence,
history failure or network failure stop without a final answer. Recovery requires
a new invocation and fresh approvals. Terminal output may describe the failure
and successful retrieval count; it never prints raw page content.

Bodies are bounded textual responses, not browser-rendered articles. HTML/script
text is never executed, JSON/XML is not parsed for external entities, and links
are never followed. Extraction of article passages and richer evidence analysis
are future work. Model prose cannot contain URLs or bracketed citations; the
application supplies the source references.

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

## Privacy and security review

The exact query reaches configured SearXNG only after approval. Its configured
upstream engines may receive the query. Each approved publisher sees the URL
path/query and ordinary connection metadata. The selected search results and
retrieved text reach configured local Ollama. Nothing in this command loads
conversation history, explicit memory, browser profiles or credential stores.

Research suppresses existing payload debug tracing. The application-start trace
redacts research arguments. Tool history receives only operation identifiers,
tool names, decisions, outcomes and timestamps; it stores no query, URL, content,
source reason or answer. Other commands retain their existing debug behavior.
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
address reaches the socket; redirect responses are aborted. Hostname verification
and platform trust roots remain enabled. TLS does not prove factual correctness.

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
./gradlew.bat test --tests 'io.kaos.app.ResearchCommandTest' --tests 'io.kaos.tool.httpget.*' --tests 'io.kaos.app.HttpGetCommandIntegrationTest' --no-daemon --warning-mode=all
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

`PinnedHttpTransportTest` exercises real response bodies, no-proxy behavior,
single DNS binding, redirect/non-200 handling, media/size/empty rejection,
deadlines, late DNS completion after cancellation, URL normalization, grant/request
binding, compressed/oversized metadata rejection and non-public DNS denial. Existing
HTTP contracts continue to test exact grants and malformed UTF-8.

An optional live demonstration uses the README command after configuring actual
publisher hosts. Review both approval prompts; redirects, non-UTF-8 pages,
oversized pages, disallowed hosts or model schema failures stop the run. The separate [live evaluation](verified-web-research-live-evaluation.md) records
local-model/public-source runs and the quality and safety problems they exposed.
The deterministic tests alone do not demonstrate public-source factual accuracy.

## Roadmap and next slice

No active duplicate research feature or child of #1094 existed at inspection.
#1095 was created and linked as the first child. #814 links Epic 019 but its
inventory still says 19 epics and omits 019. The stale inventory is recorded here;
this feature does not revise unrelated roadmap scope.

The smallest next feature is 019.02, Candidate Source Selection: improve selection
of suitable primary sources and explain exclusions before approval, without
retries or extra retrieval. Rich partial-result synthesis, robust publisher
independence checks, passage-level attribution and semantic evidence validation
remain unimplemented. The epic stays open.
