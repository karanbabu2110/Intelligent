# Epic 020 hybrid web search demonstration

[Feature 020.08](https://github.com/karanbabu2110/KAOS/issues/1108) connects the
existing, separately approved browser search fallback to `research`. The same
foreground run can now search SearXNG, optionally search Bing through a fresh
Chromium context, merge discovery results, select up to three URLs, try bounded
direct HTTP first, and offer a separate browser render for eligible direct
failures. The model never selects a browser operation or supplies an executable
URL beyond the returned, validated search candidates.

## Repeatable local demonstration

Run on Windows with Java 21 and the pinned Chromium installed by
`./gradlew.bat installChromium`:

```powershell
./gradlew.bat --% --console=plain test --tests io.kaos.app.ResearchCommandTest --tests io.kaos.app.WebSearchIntegrationTest --tests io.kaos.app.research.ResearchBrowserRendererTest --tests io.kaos.tool.websearch.WebSearchResultMergerTest --tests io.kaos.app.browser.BrowserSessionCommandTest
./gradlew.bat --% clean verifyLocal --no-daemon --warning-mode=all --console=plain
git diff --check
```

The tests use loopback SearXNG and publisher fixtures and a deterministic local
model-protocol fixture. `ResearchBrowserRendererTest` launches real headless
Chromium against fulfilled fixture resources; it does not contact public sites.

| Path | Deterministic evidence |
| --- | --- |
| Sufficient SearXNG, direct page | `ResearchCommandTest.sufficientSearchAndDirectPageUseNeitherBrowser`: quality is `SUFFICIENT`, direct HTTP succeeds, and neither browser function is called. |
| Sparse/empty SearXNG | `hybridResearchMergesSearchSourcesThenUsesDirectAndBrowserPageEvidence` and `emptySearchCanUseSeparatelyApprovedBrowserDiscovery`: separate browser-search approval, primary-first merge, deduplication, provenance and model selection from merged results. |
| Direct HTTP and browser page | The hybrid test selects two URLs; one remains `DIRECT-HTTP`, while a 401 source receives a separate browser-render approval and becomes `BROWSER-RENDERED`. |
| Denial and search failures | `deniedOrFailedBrowserSearchRetainsPrimaryDiscoveryWithoutRetry` covers denial, CAPTCHA, access denial, rate limit and timeout with one attempt and retained SearXNG results. The renderer tests classify those failures in actual Chromium fixture runs. |
| Provenance and search-only | `browserSearchCannotForgePrimaryProvenanceOrContinueAfterInterruption` rejects forged provider evidence and stops interruption; `searchOnlyOutcomeKeepsBrowserDiscoveryProvenance` retains browser origin without presenting snippets as page facts. `WebSearchResultMergerTest` checks normalized URL deduplication and distinct-page preservation. |
| Cleanup and existing CLI | `ResearchBrowserRendererTest` exercises context/slot reuse after success, timeout and failure; `BrowserSessionCommandTest` and the full `verifyLocal` suite cover the existing browser CLI and application smoke paths. |

The browser runner holds one shared session slot across search and retrieval,
uses a fresh profile-free context, pins public destinations, blocks unrelated
network traffic, enforces request/byte/time/text limits, and releases resources
on terminal paths. Browser search and page rendering each require their own
one-use approval. Publisher host approval does not authorize either operation.
Failed browser operations report fixed reasons and duration; debug lifecycle
events contain mode, outcome, duration and slot-release state, not query or page
content. Search results with recognized credential-bearing query keys are
discarded before display or model use. Retrieved pages and search snippets
remain untrusted model input.

## Optional public run

Start local SearXNG and Ollama, then use a dedicated approvals file if you do
not want this demonstration to change your normal saved publisher hosts:

```powershell
cd "D:\Study\AI\AI OS\Repos\KAOS"
$env:KAOS_OLLAMA_MODEL = 'qwen3.5:latest'
$env:KAOS_OLLAMA_CONTEXT_WINDOW = '32768'
$env:KAOS_OLLAMA_THINKING = 'off'
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = '2048'
$env:KAOS_WEB_SEARCH_SEARXNG_URL = 'http://127.0.0.1:8080'
$env:KAOS_WEB_SEARCH_BROWSER_FALLBACK_ENABLED = 'true'
$env:KAOS_WEB_SEARCH_BROWSER_MIN_ENGINES = '4'
$env:KAOS_RESEARCH_APPROVED_HOSTS_FILE = Join-Path $env:TEMP 'kaos-hybrid-demo-hosts.txt'
.\gradlew.bat --% --console=plain run --args="research \"OpenJDK JDK 26 release notes\""
```

Review each search, new publisher-host and exact browser-render prompt before
approving. Remove the dedicated approvals file to revoke any hosts saved during
the run. Search engines and publishers can change behavior between runs.

On 2026-10-08, local SearXNG was healthy and a direct JSON query for `OpenJDK
JDK 26 release notes` returned ten results from Bing; three other configured
engines were unresponsive. A public `research` run with `qwen3.5:4b` and a
longer question stopped at strict source selection. A second run with
`qwen3.5:latest` reached `LOW_ENGINE_DIVERSITY` (five retained results, three
domains, two contributing engines, two failed engines), separately approved
Bing browser search and reported `REQUEST_LIMIT`. KAOS retained the SearXNG
results. The model selected three publisher URLs; all direct reads reported
`UNAVAILABLE`, and separately approved browser renders also reported
`UNAVAILABLE`. The run completed with a labeled `Search-only outcome` and no
`FACT` claims. This is a live failure-containment example, not a successful
publisher retrieval or factual verification. The generated search-only prose
contained claims that were not checked against pages.

A separate `web-search` public check found a search-result URL with a
credential-bearing query key. The discovery boundary now discards such URLs
before display or model use. The approved Bing browser attempt still ended in
`TIMEOUT`; increasing the attempted-request ceiling did not produce a usable
result, so the existing 32-request and 20-second bounds remain.

A follow-up public run for `OpenJDK JDK 26 early access` selected
`https://openjdk.org/index.html` and `https://adoptium.net/`, and both direct
HTTP reads succeeded. A third selected publisher failed direct HTTP and
browser rendering. The run correctly reported `retrieved 2/3` and excluded the
failed source, then stopped at `SYNTHESIS_INVALID_OR_UNAVAILABLE` because the
local model did not return an acceptable final answer. This proves public page
retrieval and partial-evidence containment, but not a successful public answer.

## Completion assessment

Deterministic local tests prove the combined success and failure branches using
the application commands and real Chromium fixture runs. Public-source success
still depends on search-provider availability, publisher network access and
strict local-model schema compliance. No CAPTCHA, 403, 429, authentication or
site restriction is bypassed. Feature 020.08 has a repeatable local exit path;
keep Epic 020 open until a controlled public run completes a sourced answer and
browser-search fallback succeeds, or the roadmap explicitly accepts the local
demonstration despite the observed provider and model limits.
