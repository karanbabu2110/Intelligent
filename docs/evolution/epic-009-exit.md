# Epic 009 Exit - First Agent Workflow

Epic [009](https://github.com/karanbabu2110/KAOS/issues/53) delivers KAOS's first
real bounded agent workflow. One foreground command can inspect one local file,
search current public information, use both in that order, or use stable model
knowledge, then synthesize one answer.

## Implemented architecture

```text
one AgentGoal
  -> one tool-free model plan proposal
  -> strict AgentPlanner validation
  -> one in-memory AgentExecution
  -> AgentExecutor current step only
  -> Epic 008 ToolRegistry / KaosTool / ToolPermissionPolicy
  -> one exact single-use approval per protected step
  -> ordered untrusted ToolResult evidence
  -> one no-tools Ollama synthesis
  -> one AgentResult
```

The model proposes the plan; it does not authorize it. Registry membership also
does not authorize agent use: the planner admits only `read_local_file` and
`web_search`. The executor delegates preparation and execution to their existing
Epic 008 adapters, preserving file root containment, symlink and metadata
revalidation, bounded UTF-8 reads, configured SearXNG origin, bounded query and
response, exact disclosure, and the rule that search result URLs are never
fetched.

## Freshness behavior

The planning contract separates stable internal knowledge, local evidence,
current public evidence, and mixed evidence. It explicitly directs the model to
choose current evidence when versions, security guidance, prices,
availability, laws, schedules, public figures, recent events, or current
recommendations can materially change the answer. The plan is still strictly
validated. If required web verification fails, `AgentResult` marks current
evidence unavailable and cannot carry a final answer. Synthesis treats tool
results as untrusted data and directs the model to prefer verified current
evidence over remembered claims and state uncertainty.

## Enforced bounds

- exactly one nonblank goal of at most 4,096 Unicode code points per command;
- exactly one generated and validated plan per run;
- exactly one of four plan shapes, at most three sequential steps and at most
  two tool steps;
- only file, search, synthesis or file, search, synthesis ordering admitted;
- current validated step only, with completed steps unable to run again;
- one independent Epic 008 approval and single-use grant for each tool step;
- terminal failed, cancelled, and completed executions cannot continue;
- one no-tools synthesis over only the completed validated evidence shape;
- no retry, fallback, replan, recursion, parallelism, background work,
  sub-agents, browser automation, destructive tools, or workflow engine.

## Failure and injection behavior

Goal and plan validation fail before tool execution. Denial, invalid approval,
EOF, interruption, configuration failure, tool failure, history failure, and
provider failure stop the current workflow. Later steps do not execute and no
automatic retry or replan occurs. Completed evidence remains available in the
terminal partial result; KAOS never claims an external action was rolled back.

Tool content has no control path back to planning. It is sent only as ordered
tool-role evidence to the final request, which advertises no tools. Prompt-like
text in a file or search snippet therefore cannot mutate the plan, create
authority, reorder a step, or trigger another call.

## Evidence and deferred capability

The [agent evaluation](agent-evaluation.md) covers stable, freshness-sensitive,
local-only, mixed, denied, invalid, EOF, cancelled, unavailable, malformed,
unbounded, disallowed, failed, injection, conflict, and success scenarios.
The representative mixed test crosses a temporary file, real tool validators
and executors, two distinct approvals, loopback SearXNG, loopback streamed
Ollama NDJSON, ordered evidence serialization, synthesis, and terminal result.
The final clean local checkpoint passed all 11 `verifyLocal` tasks with 515
tests across 69 suites: 511 passed, zero failed or errored, and four documented
platform-specific symlink checks were skipped.

Agent state is intentionally in memory only. There is no resume after restart,
agent database, arbitrary model-authored workflow, URL fetching, or browser
interaction. Epic 010 may add browser automation only as another explicitly
bounded capability; it can reuse the proven separation between model proposal,
KAOS validation, user authority, concrete execution, and untrusted evidence. It
must not weaken or silently generalize the Epic 009 agent boundary.

Post-exit Feature 009.10 strengthens this closed epic with an independent
three-level [freshness policy](freshness-policy-hardening.md). Clearly required
current evidence is now a plan invariant, recommended evidence remains
observable and nonblocking, and an empty search result cannot count as verified
or proceed to synthesis. The original workflow and tool-authority limits remain
unchanged.
