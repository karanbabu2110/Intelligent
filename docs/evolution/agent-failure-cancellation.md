# Agent Failure and Cancellation

Feature [009.07](https://github.com/karanbabu2110/KAOS/issues/906)
makes the first bounded agent stop simply, safely, and observably when its
validated workflow cannot continue.

## Implemented terminal behavior

`AgentExecution` now records one content-free `AgentFailureReason` on both the
overall execution and the current failed or cancelled step. A terminal
execution cannot prepare, approve, execute, or complete another step.

```text
current step fails or is cancelled
  -> mark that step FAILED or CANCELLED
  -> mark the execution FAILED or CANCELLED
  -> preserve earlier completed ToolResult evidence
  -> reject every later execution attempt
```

There is no retry counter because there is no retry. The coordinator does not
ask the planner for another plan, choose a fallback tool, skip to a later step,
or reinterpret tool-result content as a recovery instruction. Completed tool
effects are historical facts and are not described as rolled back.

## Failure boundaries

Failures before a valid execution exists remain explicit constructor/planning
exceptions:

- invalid goal input is rejected by `AgentGoal`;
- malformed or structurally invalid plans are rejected by `AgentPlanner`; and
- unknown or agent-disallowed tools are rejected before execution by the
  planner's existing Epic 008 registry and selection checks.

Failures during a valid execution receive a stable content-free reason:

| Boundary | Terminal status | Reason |
| --- | --- | --- |
| tool configuration cannot be loaded | `FAILED` | `TOOL_CONFIGURATION_UNAVAILABLE` |
| exact tool request/resource validation fails | `FAILED` | `TOOL_VALIDATION_FAILED` |
| user denies the exact operation | `FAILED` | `PERMISSION_DENIED` |
| approval response or decision use is invalid | `FAILED` | `INVALID_APPROVAL` |
| approval input reaches EOF | `CANCELLED` | `END_OF_INPUT` |
| explicit cancellation | `CANCELLED` | `CANCELLED` |
| thread interruption wins the approval race | `CANCELLED` | `INTERRUPTED` |
| concrete tool execution fails | `FAILED` | `TOOL_EXECUTION_FAILED` |
| tool returns evidence for a different request | `FAILED` | `INVALID_TOOL_RESULT` |
| synthesis/model provider fails | `FAILED` | `MODEL_PROVIDER_FAILED` |

The enum also names `INVALID_GOAL`, `INVALID_PLAN`, and `TOOL_NOT_ALLOWED` so
the next result boundary can report pre-execution failures without copying
exception text or user content. This feature does not weaken the earlier
fail-fast validation paths or create an execution for an invalid plan.

## Evidence and truthfulness

Completed `ToolResult` values remain available through the execution's
explicit private evidence accessor after a later failure. Snapshots and
`toString` expose only result availability, counts, identifiers, status, and
the content-free reason; they do not leak goal, file, query, or result content.

If current-public verification fails after local evidence succeeds, the
execution stays incomplete. Nothing in this feature substitutes model memory
for the failed search or labels stale knowledge as verified current evidence.
The structured user-facing partial summary that communicates this distinction
is the next ordered feature.

## Deterministic evidence

The focused agent tests prove configuration failure, invalid local targets,
permission denial, invalid approval, EOF, cancellation, interruption, concrete
tool failure, mismatched results, model-provider failure, preserved prior
evidence, terminal-state enforcement, and no second preparation or execution.
They use temporary files and loopback HTTP fixtures and require no public
internet, live Ollama, live SearXNG, external API, or database.

## Deliberate limits and handoff

This feature adds no compensation, rollback claim, retry, fallback, dynamic
replanning, step skipping, parallel/background execution, persistence,
restart recovery, nested agent, browser automation, or generic workflow error
framework.

There is not yet a user-facing agent command. The next ordered feature is
[009.08 - Result Summary](https://github.com/karanbabu2110/KAOS/issues/907).
It should turn this state and preserved evidence into a small truthful result,
including incomplete current-verification outcomes.
