# Agent Evaluation

Feature [009.09](https://github.com/karanbabu2110/KAOS/issues/908) is the
Epic 009 exit gate. It evaluates the real foreground `agent` command and the
code boundaries introduced by Features 009.01 through 009.08. The suite uses
deterministic model doubles, temporary files, real loopback Ollama NDJSON, and a
real loopback SearXNG HTTP fixture. It requires no public internet, installed
model, live Ollama, or live SearXNG.

## Evaluated runtime

`AgentCommand` accepts one bounded `AgentGoal`, submits one tool-free planning
request, and gives the returned JSON to `AgentPlanner`. Only a validated plan is
given to `AgentExecutor`. Each tool step resolves through `ToolRegistry`, uses
the concrete `KaosTool` and `ToolPermissionPolicy`, records the terminal policy
snapshot through the existing tool history, and advances the in-memory
`AgentExecution`. Completed evidence is supplied to exactly one no-tools Ollama
synthesis. `AgentResult` is the only terminal workflow summary.

## Deterministic scenario matrix

| Scenario | Code-level proof |
| --- | --- |
| Stable explanation | Synthesis runs with no tool preparation or approval. |
| Freshness-sensitive goal | The accepted plan contains one approved `web_search`. |
| Local-only goal | One approved `read_local_file` runs and search does not. |
| Mixed local and current goal | Real temporary-file read, loopback SearXNG request, and loopback Ollama synthesis occur in exact file, search, synthesis order. |
| File approval denied | The first step terminates; search and synthesis never run. |
| Search approval denied | Completed local evidence remains visible; synthesis never runs. |
| Invalid approval and EOF | Neither condition creates execution authority. |
| Cancellation and interruption | The current execution becomes cancelled and later steps do not run. |
| Search unavailable | One failed attempt yields an incomplete result with required current evidence unavailable and no answer. |
| Malformed or unbounded plan | Malformed JSON, more than three steps, more than two tool steps, unknown tools, and registered-but-disallowed `http_get` are rejected before execution. |
| Tool or model failure | The run stops with no retry, fallback, replan, later step, or answer. |
| Tool-history failure | The completed external action remains reported, no rollback is claimed, and synthesis does not run. |
| Prompt injection in a result | Injection text remains a tool-role evidence value and cannot alter the already validated plan or trigger a tool. |
| Current evidence conflicts with memory | The synthesis instruction requires verified current evidence to win; the full fixture proves the returned answer uses the current value. |
| Successful bounded workflow | One goal produces one plan, two separate approvals, two ordered tool attempts, one no-tools synthesis, and one completed result. |

`OllamaPromptClientTest` separately rejects null, duplicate, reversed,
over-two-step, and disallowed evidence lists before opening a network
connection. Existing agent package tests continue to prove goal, plan,
execution-state, approval, failure, cancellation, and result invariants in
isolation.

## Realism and limitations

The representative mixed scenario crosses actual filesystem and HTTP transport
boundaries, including streamed NDJSON and SearXNG JSON parsing. The servers are
loopback fixtures so the result is repeatable and cannot drift with public
services. This is stronger than class-existence testing but is not a claim that
an arbitrary installed model always emits a useful plan or that a public search
service is reachable. Operator configuration and provider quality remain runtime
dependencies.

There is deliberately no automatic retry, fallback execution, dynamic
replanning, parallel or background execution, persistence or restart resume,
nested agent, browser automation, destructive tool, or generic workflow engine.

## Verification checkpoint

The forced focused command executed 117 tests across 10 suites with zero
failures, errors, or skips. The clean repository checkpoint executed all 11
`verifyLocal` tasks and 515 tests across 69 suites: 511 passed, zero failed,
zero errored, and four existing platform-specific symlink scenarios were
skipped. The architecture page was also rendered at 1,440 by 900 and 390 by
844 CSS pixels: it had no page-level horizontal overflow, exactly one current
feature marker, and no browser console warnings or errors.
