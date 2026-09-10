# Agent Result Summary

Feature [009.08](https://github.com/karanbabu2110/KAOS/issues/907)
introduces one immutable, truthful result boundary for a terminal bounded agent
execution.

## Implemented result

`AgentResult` is derived from exactly one `AgentExecution` and records:

- execution identity and the original immutable goal;
- final `COMPLETED`, `FAILED`, or `CANCELLED` status;
- ordered completed-step snapshots;
- the failed or cancelled step when present;
- completed bounded `ToolResult` evidence;
- public title/URL references from completed `web_search` evidence;
- the content-free terminal reason;
- current-evidence status; and
- one bounded final answer only for a completed execution.

The result has no setters and copies every list. It is not persisted.

## Success and incomplete construction

The two construction paths are deliberately separate:

```text
all validated steps COMPLETED
  -> AgentResult.completed(execution, bounded answer)

execution FAILED or CANCELLED
  -> AgentResult.incomplete(execution)
  -> no answer accepted
```

The completed factory rejects a planned, running, failed, or cancelled
execution. The incomplete factory rejects a planned, running, or completed
execution. Answers must be nonblank, contain no unsafe control characters, and
remain within the existing 65,536-code-point local-model response ceiling.

This feature does not ask the model to invent an answer after a failure. A
failed or cancelled result always has an empty `answer`, so unavailable current
evidence cannot silently become a supposedly current model-memory conclusion.

## Freshness integrity

`CurrentEvidenceStatus` makes the evidence boundary explicit:

| Status | Meaning |
| --- | --- |
| `NOT_REQUIRED` | the validated plan was stable-internal or local-only |
| `VERIFIED` | the required bounded `web_search` step completed with a matching result |
| `REQUIRED_BUT_UNAVAILABLE` | the validated plan required current evidence but search did not complete |

A completed search remains `VERIFIED` if later synthesis fails, while the whole
run remains failed and answer-free. If search fails after a local read, the
local evidence and completed-step summary remain available, but freshness is
explicitly unavailable.

Successful synthesis may therefore prefer verified current evidence over stale
model memory and retain the search result's public source title and URL. The
result model does not claim that arbitrary model prose is factually correct;
the deterministic end-to-end synthesis policy and contradiction scenario are
the Epic exit-gate work in 009.09.

## Evidence and privacy

Raw completed evidence remains available only through the explicit `evidence`
accessor needed for synthesis and partial findings. Generic `toString`
diagnostics expose only identifiers, statuses, reason enums, availability, and
counts. They do not expose the objective, local path/content, search query,
source title/URL, or final answer. Source-reference diagnostics are also
redacted.

Tool evidence remains data. `AgentResult` never parses evidence content as a
plan, command, approval, tool selection, or recovery instruction. Constructing
a result cannot add a step, execute a tool, retry, or replan.

## Deterministic evidence

`AgentResultTest` proves:

- stable completed success without an unnecessary freshness claim;
- mixed local/current success with ordered evidence and public citations;
- current evidence superseding an older remembered value in the supplied
  consolidated answer;
- unavailable search after a completed local read producing an answer-free
  partial result;
- provider failure after successful search retaining verified evidence but no
  answer;
- distinct denied and cancelled summaries;
- rejection of nonterminal/mismatched construction and invalid answers; and
- content-free generic diagnostics, including prompt-injection-shaped local
  evidence.

The tests use only deterministic values and existing bounded result types. They
require no public internet, live Ollama, live SearXNG, external API, or database.

## Deliberate limits and handoff

This feature adds no result renderer, persistence, streaming result events,
retry, fallback, replanning, parallel/background execution, nested agent,
browser automation, or generic workflow result hierarchy.

There is not yet a user-facing end-to-end agent command. The next ordered
feature is the Epic exit gate,
[009.09 - Agent Evaluation](https://github.com/karanbabu2110/KAOS/issues/908),
which must integrate and prove goal through final synthesis/result using local
fixtures and deterministic model responses.
