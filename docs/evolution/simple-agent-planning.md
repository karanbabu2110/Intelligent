# Simple Agent Step Planning

Feature [009.03](https://github.com/karanbabu2110/KAOS/issues/902)
turns one [bounded agent goal](agent-goal-representation.md) and one structured
model proposal into one immutable validated sequential plan. Planning has no
tool, permission, filesystem, network, or persistence side effect.

## Implemented model proposal

The planner accepts JSON only. The root contains exactly `informationNeed` and
`steps`. The four allowed information needs are:

- `STABLE_INTERNAL`;
- `LOCAL_EVIDENCE`;
- `CURRENT_PUBLIC_EVIDENCE`; and
- `MIXED_EVIDENCE`.

A tool step contains exactly an integer `sequence`, type `tool`, a `tool` name,
and an `arguments` object. A synthesis step contains only its integer sequence
and type `synthesis`. For example:

```json
{
  "informationNeed": "MIXED_EVIDENCE",
  "steps": [
    {
      "sequence": 1,
      "type": "tool",
      "tool": "read_local_file",
      "arguments": {"path": "docs/evolution/epic-008-exit.md"}
    },
    {
      "sequence": 2,
      "type": "tool",
      "tool": "web_search",
      "arguments": {"query": "current SearXNG security guidance"}
    },
    {"sequence": 3, "type": "synthesis"}
  ]
}
```

The proposal is bounded to 8,192 Unicode code points before JSON parsing. The
parser also rejects duplicate fields, trailing JSON values, excessive nesting,
oversized field names, Markdown fences, arbitrary class names, commands, extra
root or step fields, non-integral sequences, and unstructured model prose.

## KAOS validation

The model proposes both an information need and steps; neither grants
authority. `AgentPlanner` validates the proposal and creates an `AgentPlan`
only when all code-level rules pass:

- at most three total steps and two tool steps;
- consecutive one-based sequence values with no duplicates or reordering;
- a synthesis step is last and occurs exactly once;
- `STABLE_INTERNAL` is synthesis only;
- `LOCAL_EVIDENCE` is `read_local_file` then synthesis;
- `CURRENT_PUBLIC_EVIDENCE` is `web_search` then synthesis;
- `MIXED_EVIDENCE` is `read_local_file`, `web_search`, then synthesis; and
- every tool name and argument object passes the existing Epic 008
  `ToolSelector` for the fixed local scope.

This exact four-shape contract is sufficient for the first agent use case and
avoids a workflow language. In particular, a freshness-sensitive proposal must
include `web_search`; stable explanatory work cannot add an unnecessary search.
Mixed evidence cannot reverse the required local-then-current order.

`ToolSelector` resolves each tool step to a defensive `ToolSelection`. That
reuses the registered implementation and concrete argument decoder while
retaining the crucial rule that a selection is not permission. The registered
`http_get` tool remains disallowed for agent plans, and unknown or malformed
tools are rejected with content-free reasons.

## Values and lifecycle

`AgentStep` has only two sealed variants: a validated tool selection or
synthesis. `AgentPlan` contains one generated UUID, the exact `AgentGoal`, one
information need, and a defensively copied ordered step list. There is no step
instruction text, executable class name, branch, dependency, retry, fallback,
or nested plan.

The planner is stateless. The later execution owner will enforce one plan per
run and prevent post-start replanning. This feature does not prepare a
permission policy, display approval, execute a selected tool, call Ollama, or
synthesize an answer. A later coordinator may obtain one JSON proposal through
the existing local model integration and pass it to this validator.

## Deterministic evidence

`AgentPlannerTest` covers all four information needs, exact mixed ordering,
exact and exceeded response bounds, null/blank/deep/duplicate/trailing JSON,
malformed and empty plans, more than three steps, more than two tool steps,
duplicate/out-of-order sequence values, evidence-shape mismatches, unknown
tools, registered-but-disallowed `http_get`, malformed arguments, instruction-
like argument data, defensive plan steps, privacy-safe diagnostics, and proof
that planning does not load tool configuration, prepare approval, or execute
tools.

Tests use structured fixed model proposals and inert tool configuration
suppliers. They require no live Ollama, SearXNG, public internet, target file,
approval input, or database.

## Deliberate limits and handoff

There is no plan persistence, workflow DSL, generic node type, DAG, parallel
planning, retry, fallback, dynamic replanning, background work, nested agent,
browser automation, or tool authority.

The next ordered feature is
[009.04 - Tool-Assisted Execution](https://github.com/karanbabu2110/KAOS/issues/903).
It should coordinate only the current validated `AgentStep.Tool` through its
existing `ToolSelection` and `ToolPermissionPolicy`, without creating another
registry, permission system, executor hierarchy, or result type.
