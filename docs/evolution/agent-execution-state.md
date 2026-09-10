# Agent Execution State

Feature [009.05](https://github.com/karanbabu2110/KAOS/issues/904)
adds one observable in-memory state owner around the
[agent tool executor](agent-tool-assisted-execution.md). It represents one
execution of one goal and its one validated plan without adding persistence or
a workflow engine.

## Implemented state

`AgentExecution` contains:

- one generated execution UUID;
- the exact immutable `AgentPlan` and its `AgentGoal`;
- one state entry for each validated plan step;
- one current sequential step index;
- completed bounded `ToolResult` evidence needed by later synthesis; and
- one overall status.

Overall statuses are `PLANNED`, `RUNNING`, `COMPLETED`, `FAILED`, and
`CANCELLED`. Step statuses are `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, and
`CANCELLED`. These are agent workflow states; the Epic 008
`ToolExecutionOutcome` remains the separate permission/tool-attempt lifecycle.

## Transition rules

Every plan step begins `PENDING`, and the execution begins `PLANNED`. Only the
current step can move to `RUNNING`:

```text
PENDING tool
  -> RUNNING after concrete permission preparation begins
  -> COMPLETED only after one matching ToolResult

PENDING synthesis
  -> RUNNING through explicit synthesis start
  -> COMPLETED through explicit synthesis completion
```

A successful tool step stores its bounded result, marks only that step
completed, and advances exactly one sequence. A completed step cannot become
current again. Synthesis cannot start while a required tool step is pending or
running. The overall execution becomes `COMPLETED` only after every validated
step is completed.

Failure marks the current pending/running step `FAILED`; cancellation marks it
`CANCELLED`. In both cases the overall execution becomes terminal and later
steps remain pending but cannot run. A terminal completed, failed, or cancelled
execution rejects all later starts and transitions. Completed earlier evidence
is retained; no completed file read or search is described as rolled back.

`AgentExecutor` now reports preparation, matching result completion, concrete
failure, denial, and cancellation into this state owner. It no longer maintains
a separate sequence or stopped flag. This preserves one source of truth for
the active run while continuing to reuse the Epic 008 permission lifecycle.

## Observation and privacy

The public state can answer which plan is running, its overall status, the
current step, every step status, whether a result is available, and how many
results completed. `StepSnapshot` identifies a tool only by its fixed safe name;
it does not contain tool arguments, file paths, search queries, approval prompts,
or result bodies.

Completed `ToolResult` values are available only through an explicit
`completedResults` accessor for later synthesis and truthful partial reporting.
The returned list is immutable. Generic state snapshots and `toString` exclude
the goal objective and evidence content.

## Tool history remains separate

Agent state answers where one bounded goal currently is and which evidence is
available. Tool history answers what terminal permission/tool attempts occurred
across commands. This feature does not store agent state in the Epic 008 SQLite
history and does not copy private result content into history.

## Deterministic evidence

`AgentExecutionTest` proves initial state, exact sequential completion, current
step observation, synthesis-only completion, invalid/out-of-order transition
rejection, terminal failure and cancellation, immutable snapshots, preserved
evidence after a later failure, and content-free diagnostics.

`AgentExecutorTest` additionally proves the state transitions around real
temporary-file and loopback-SearXNG execution, denial, unavailable
configuration, concrete execution failure, and mismatched result rejection.
Tests require no public internet, live Ollama, live SearXNG, external API, or
database.

## Deliberate limits and handoff

State exists only in the foreground Java process. There is no SQLite agent
database, resume after restart, checkpoint recovery, state repository, event
stream, retry, fallback, replanning, parallel execution, background work,
nested agent, or browser automation.

This feature exposes the existing permission object but does not yet own user
approval input or add an explicit approval-checkpoint observation. The next
ordered feature is
[009.06 - User Approval Checkpoints](https://github.com/karanbabu2110/KAOS/issues/905).
It should coordinate each current tool policy's exact prompt and one decision
without creating another approval system or plan-wide authority.
