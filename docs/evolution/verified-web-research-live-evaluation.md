# Live scenario evaluation — 16 September 2026

This records manual evidence for [Feature 019.01 / #1095](https://github.com/karanbabu2110/KAOS/issues/1095).
Live model outputs are observations, not assertions that the reported public
facts are correct. Search results and model responses vary between invocations.

## Environment and method

- Local Ollama at `127.0.0.1:11434`, installed `qwen3.5:4b`, thinking off.
- Packaged CLI scenarios explicitly used an 8192-token context window.
- SearXNG at `127.0.0.1:8080`; Docker Desktop and its existing SearXNG container
  were initially stopped. Docker Desktop was started; the existing loopback-only
  container restarted and became healthy. No Compose configuration was changed.
- Debug enabled with separate files under
  `%TEMP%\kaos-live-scenarios-20260916-1514`. Test tool history was isolated there.
  Existing user debug files and ordinary history were not overwritten.
- Most scenarios ran the packaged application from the verified distribution;
  scenario 11 used the actual Gradle command below. Inputs supplied fresh
  approvals/denials for each separate run; no grant was reused across runs.
- Research allowed hosts were limited to official JDK/Oracle domains. No
  allowlist was widened in response to a disallowed selection.

## Observed scenarios

| Case | Scenario | Observed outcome |
| --- | --- | --- |
| 01 | Original Nepal flood question with SearXNG stopped | `SEARCH_SERVICE_UNAVAILABLE`; no final answer |
| 02 | Original Nepal flood question after startup | Agent completed from five search snippets; pages were not fetched |
| 03 | Nepal question explicitly dated 16 September 2026 | Agent completed, identified a reported event date and acknowledged missing casualty evidence |
| 04 | Explain binary search | Internal synthesis completed without web search |
| 05 | Deny latest-Java search | `PERMISSION_DENIED`; no search or answer; existing agent denial exit code is 0 |
| 06–07 | Broad latest-Java research, with denial/approval inputs queued | Both stopped at `DISALLOWED_HOST` before source approval; these did not exercise their intended second-checkpoint decisions |
| 08 | Research scoped to `jdk.java.net`, deny proposed set | Three exact URLs displayed; `SOURCES_NOT_APPROVED`; zero retrievals |
| 09 | Same scoped research, approve set | One source retrieved; second failed; stopped at `1/3`, without synthesis or attempting the third |
| 10 | Overly specific JDK 21 query | `NO_SEARCH_RESULTS`; no selection, retrieval or answer |
| 11 | Original Nepal query through Gradle | Command quoting worked; a changed result set contained formatting characters and produced `SEARCH_INVALID_RESPONSE`; no answer |
| 12 | Short JDK 21 research query | One source retrieved and cited synthesis completed, but its availability claim contradicted the page |
| 13 | Standalone `http-get` review of the same approved JDK page | Retrieved text confirmed that archived releases remain available; this exposed the contradiction in case 12 |
| 14 | Same research query after synthesis-instruction refinement | Search returned a token-bearing duplicate URL; two pages were retrieved, then `MODEL_LOCAL_LIMIT_REACHED` stopped synthesis. This exposed missing credential-query rejection, fixed afterward |
| 15 | Final build, query excluding token-bearing variants | `NO_SEARCH_RESULTS`; stopped without retrieval or synthesis |

The prompt refinement has not been shown to correct the factual error in a
subsequent live answer: cases 14 and 15 stopped before synthesis. The issue is
therefore still recorded as a residual semantic-quality risk.

Case 12 demonstrates a complete live mechanical path, including HTTPS and TLS,
but **fails factual-quality review**. It must not be counted as a verified factual
answer merely because the command returned zero or citations were valid.

## Findings and changes

1. The broad Nepal answer combined numbers from snippets without clearly
   attaching each figure to a report. The explicit-date question was more cautious,
   but still did not prove that the returned event was the most recent. Existing
   `agent` remains snippet-based; its control flow was not expanded for research.
2. Candidate selection sometimes called an official page ANECDOTAL or treated
   URL variants from the same publisher as independent evidence. These remain
   model-quality limitations and inform Feature 019.02.
3. The original JDK research synthesis equated superseded releases with
   unavailable releases, despite the page pointing to archive availability.
   The synthesis instruction now explicitly preserves qualifiers, negation,
   dates and publisher/product scope, and distinguishes superseded, archived,
   unavailable and unsupported. This is prompt hardening, not an entailment proof.
4. A search result included an authentication-style query parameter. Before the
   fix, the shared validator allowed it and the approved test sent it. The shared
   HTTP validator now rejects recognized token/password/secret/API-key/session/
   credential/signature parameter names, including encoded names, before approval.
   Tests cover both the validator and the research checkpoint. The token value
   is deliberately omitted from this report. Arbitrary server-specific credential
   conventions remain outside what URL syntax alone can establish.
5. The Gradle Nepal run received HTTP 200 JSON, but a snippet contained Unicode
   word-joiner and zero-width non-joiner characters. Existing `TextBounds` rejects
   those FORMAT characters, which caused the whole bounded result to fail.
   The follow-up fix removes only U+2060 and U+200C from titles/snippets at the
   SearXNG boundary, checking original field lengths first. URLs and queries are
   unchanged; bidi controls and other invalid fields still reject the result.
   Two local-fixture regression tests cover normalization and preserved rejection.
   The exact Gradle Nepal command then completed search and synthesis in 15 seconds.
   It still provides snippet-only evidence, not verified page-based claims.
6. Research debug logs contained only redacted startup/command/end metadata.
   Agent and standalone HTTP debug logs retained their existing payload behavior.
   Raw live logs remain local and must be reviewed before sharing.

## Reproduction

Use literal PowerShell values, without Markdown link syntax, escaped underscores
or copied continuation prompts:

```powershell
$env:KAOS_WEB_SEARCH_SEARXNG_URL = "http://127.0.0.1:8080"
$env:KAOS_OLLAMA_MODEL = "qwen3.5:4b"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_DEBUG = "true"
$env:KAOS_DEBUG_FILE = Join-Path $env:TEMP "kaos-debug.jsonl"

.\gradlew.bat --% --console=plain run --args="agent \"Tell me about recent nepal flood incident\""
```

For research, configure the relevant exact host and use a focused question:

```powershell
$env:KAOS_HTTP_ALLOWED_HOSTS = "jdk.java.net"
.\gradlew.bat --% --console=plain run --args="research \"site:jdk.java.net/21/ JDK 21 release status -inurl:accessToken\""
```

Review each exact query and source set before typing `approve` or `deny`.
The search exclusion above is a query choice, not a substitute for application
credential validation. The final application independently rejects recognized
credential-bearing URLs regardless of search syntax.

## Final repository validation

After the live-discovered credential-query fix (before search normalization):

- Focused research/HTTP suite: 35 tests passed.
- `./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all`: 579 tests,
  575 passed, four existing Windows symlink skips, no failures/errors; all 11
  verification tasks passed, including packaging, status and help.
- The prior synthesis-only instruction change was also fully verified before
  its live recheck. No existing test was removed or relaxed.
- Distribution SHA-256 at that checkpoint: `66f386dec30b28d9b85606cd93acfa687df539f096943845926271424bf7f9d9`.

After the search-normalization follow-up:

- Focused `io.kaos.tool.websearch.*` and `io.kaos.app.ResearchCommandTest`: 24 passed.
- Exact live Nepal command: successful search and synthesis, five normalized
  results; nine joining hints in the raw first-five snippets and none in normalized
  snippets. Local trace: `%TEMP%/kaos-search-normalization-recheck.jsonl`.
- `./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all`: 581 tests,
  577 passed, four existing skips, zero failures/errors; all 11 tasks passed.
- Final distribution SHA-256: `28df00852de1c4cf312ef4b205148bfd0992a810107d4cfcf78cd76ab1bf957d`.

## Completion boundary

The live evaluation proves working local integration and several safe stopping
paths. It also demonstrates why mechanical success and passing deterministic
tests do not establish factual correctness. Source suitability, source independence,
claim scope and semantic support still need work. Epic 019 remains open.

The recommended next slice is 019.02: improve candidate selection, explain
exclusions, distinguish publisher roles and avoid duplicate variants before
approval. No later feature issue was created during this evaluation.
