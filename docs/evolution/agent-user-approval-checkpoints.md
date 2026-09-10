# Agent User Approval Checkpoints

Feature [009.06](https://github.com/karanbabu2110/KAOS/issues/905)
integrates independent approval checkpoints into the bounded multi-step agent
execution while reusing the complete Epic 008 permission system.

## Implemented checkpoint flow

For each current planned tool step, `AgentExecutor` now supports exactly this
interaction:

```text
prepareCurrent
  -> concrete KaosTool prepares its exact ToolPermissionPolicy
currentApprovalPrompt
  -> caller displays that concrete policy prompt without rewriting it
decideCurrent(one response) or cancelCurrent
  -> concrete policy parses the decision and creates at most one grant
executeCurrent
  -> possible only after APPROVED
```

There is no agent approval type. `currentApprovalPrompt` returns the existing
file/search prompt verbatim. `decideCurrent` delegates directly to the existing
`ToolPermissionPolicy.decide`; `cancelCurrent` delegates to its existing
`cancel`. The decisions remain `APPROVED`, `DENIED`, `INVALID_RESPONSE`,
`END_OF_INPUT`, and `CANCELLED`.

The application layer that later exposes the agent command must print the
returned prompt and supply one bounded local-user response, just as current
tool commands do. This feature does not create another input reader or weaken
the existing bounded approval-input convention.

## Independent step authority

A mixed plan produces two different permission policies and operation IDs:

```text
Step 1 read_local_file -> exact file prompt -> decision/grant A -> one read
Step 2 web_search      -> exact query prompt -> decision/grant B -> one search
```

Completing Step 1 discards its consumed policy before Step 2 is prepared.
Approval A cannot execute Step 2: the new search policy remains
`APPROVAL_REQUIRED` until it receives its own response. There is no plan-wide,
session-wide, persistent, reusable, or approve-all authority.

The file prompt continues to expose the exact normalized target, byte count,
and local-Ollama disclosure. The search prompt continues to expose the exact
query, configured self-hosted SearXNG recipient, and possible forwarding to
external search engines. Agent orchestration does not hide or summarize these
security-relevant details.

## Fail-closed behavior

- `approve` creates one operation-specific grant; any different casing or word
  remains invalid under the existing strict parser.
- `deny` marks the current step and execution failed without tool execution.
- an invalid response marks the current step and execution failed without tool
  execution;
- EOF maps to `END_OF_INPUT` and cancels the current step/execution;
- explicit cancellation maps to `CANCELLED` without a grant;
- an interrupt that arrives before or during the decision wins over arriving
  approval text and cancels; and
- a second decision against the same policy fails and creates no replacement
  authority.

Every non-approval immediately makes the agent execution terminal, so no later
pending step can be prepared. Evidence from an earlier completed step remains
available in `AgentExecution.completedResults` for later truthful partial
reporting.

## Deterministic evidence

`AgentApprovalCheckpointTest` uses a temporary real file and the existing
concrete tool adapters to prove exact prompts, different per-step operation
identities, approval non-transfer, file denial, search denial after a successful
file step, invalid response, EOF, explicit cancellation, interruption, repeated
decision rejection, terminal state, and preserved prior evidence.

The focused selection also retains the concrete executor and shared permission
runtime tests. It performs no public-internet request and requires no live
Ollama, SearXNG, external API, or database.

## Deliberate limits and handoff

This feature does not add persistent trust, remembered decisions, plan
approval, auto-approval, approval policies, roles, remote users, another grant,
another permission store, retry, fallback, replanning, parallel/background
execution, nested agents, or browser automation.

There is not yet a user-facing agent command. The next ordered feature is
[009.07 - Failure and Cancellation](https://github.com/karanbabu2110/KAOS/issues/906).
It should classify preparation, permission, tool, interruption, and model
failures for truthful terminal reporting without retrying, replanning, or
pretending completed external actions were rolled back.
