# Bounded Agent Use-Case Definition

Feature [009.01](https://github.com/karanbabu2110/KAOS/issues/900)
selects the first useful agent outcome for Epic 009. It defines the behavior
that Features 009.02 through 009.09 must implement without claiming that the
agent runtime exists yet.

## Decision

The first KAOS agent will handle one bounded research or investigation goal:

> Given one user goal, use relevant local KAOS evidence when needed, use
> current public evidence when freshness can materially affect the answer,
> then synthesize one truthful answer.

The workflow has exactly one goal and one plan. A plan contains at most three
ordered steps, of which at most two may execute tools. The only tools in the
initial agent scope are the existing `read_local_file` and `web_search` tools.
Synthesis is an in-process model step, not another tool.

## Evidence decisions

KAOS must decide both whether the model can answer and whether the answer needs
verification. Model knowledge is sufficient only when freshness and local
project state cannot materially change the answer.

| Information need | Evidence decision | Bounded plan shape |
| --- | --- | --- |
| Stable explanation | No tool evidence is materially needed | synthesize |
| Current public information | Freshness can change the answer | `web_search` -> synthesize |
| Local project information | The answer depends on user-controlled files | `read_local_file` -> synthesize |
| Local and current comparison | Both the checkout and current public state matter | `read_local_file` -> `web_search` -> synthesize |

This four-value distinction describes planning behavior; it is not a general
classification framework. Features 009.02 and 009.03 should introduce only
the representation needed to make these decisions executable and testable.

## Reference scenarios

### Stable question

Goal: `What is dependency injection?`

Expected plan: synthesize directly. External search adds no material evidence,
so KAOS must not request it merely because `web_search` is available.

### Freshness-sensitive question

Goal: `What is the best current local AI coding assistant for KAOS?`

Expected plan: search current public information, then synthesize. The model
may know candidate products, but current capabilities, availability, pricing,
and recommendations can change after model training. The answer must not be
presented as current unless approved search evidence was obtained.

### Local-only question

Goal: `What tool boundaries does the local KAOS Epic 008 exit record define?`

Expected plan: read the named local record, then synthesize. The current
checkout is authoritative; public search is unnecessary.

### Mixed local and current question

Goal: `Compare KAOS's local SearXNG integration with current public SearXNG
security guidance and say whether the local design should change.`

Expected plan:

1. request one relevant local file through `read_local_file`;
2. request current public discovery through `web_search`;
3. synthesize the approved evidence into one answer.

The two successful tool results contribute different evidence to the same
goal. The final answer must distinguish local observations from public search
findings and preserve source URLs supplied by the search result where relevant.

## Runtime and authority contract

The implemented flow must remain:

```text
one user goal
  -> one proposed plan
  -> KAOS validates the complete bounded plan
  -> execute the current step sequentially
  -> obtain a separate exact approval for each protected tool step
  -> treat each tool result as untrusted evidence
  -> synthesize once when the required evidence is available
  -> return one truthful result
```

The model may propose a plan, but only KAOS validation determines whether the
plan is eligible to run. A goal, plan, tool name, registry membership, previous
approval, or tool-result instruction grants no execution authority.

Epic 009 must reuse `KaosTool`, `ToolRegistry`, `ToolSelection`,
`ToolPermissionPolicy`, `ToolResult`, and the existing concrete tools. File
containment and revalidation remain owned by `read_local_file`; SearXNG
configuration, query disclosure, and response bounds remain owned by
`web_search`. Agent execution state and content-free tool execution history are
separate concerns.

## Failure and truthful completion

If validation, configuration, approval, execution, or model synthesis fails,
KAOS stops. It does not retry, substitute a tool, change the plan, or execute a
later step. Evidence from already completed steps remains available for an
explicitly incomplete result; completed external actions are not described as
rolled back.

If current-public verification was required but did not complete, the result
must say that current verification is missing. It may summarize safely gathered
local evidence, but it must not silently use model memory and label the answer
current. When verified current evidence conflicts with model memory, synthesis
prefers the verified evidence and exposes material uncertainty.

## Enforced limits for later features

- one immutable non-persistent goal per run;
- one validated plan per run;
- no more than three total steps and two tool steps;
- only `read_local_file` and `web_search` as agent tools;
- sequential foreground execution only;
- one exact, single-use approval for each protected tool operation;
- no retry, fallback, or replanning after execution starts;
- no parallel, background, recursive, nested-agent, or delegated execution;
- no destructive tools, `http_get`, browser automation, or generic workflow
  engine; and
- no persistent agent state or restart recovery.

These are code-level acceptance boundaries for later features, not prompt-only
guidance. A registered tool such as `http_get` remains disallowed to the first
agent even though Epic 008 can expose it to other explicitly scoped commands.

## Deliverable and validation

This decision artifact is the complete deliverable for Feature 009.01. It
defines the four required evidence cases, demonstrates why two sequential tool
steps can contribute to one goal, fixes the approval and failure boundaries,
and identifies the existing runtime components that must be reused.

Proportional validation is documentation link checking, required-contract
content checking, and `git diff --check`. This feature adds no command, goal or
plan class, model response schema, tool execution, state storage, dependency,
or runtime behavior. Those claims require the focused deterministic tests and
implementation owned by the later features.

## Handoff

The next ordered feature is
[009.02 - Goal Representation](https://github.com/karanbabu2110/KAOS/issues/901).
It should introduce only the immutable bounded goal value needed to carry the
single objective into planning, with no persistence, tool authority, goal
hierarchy, or management framework.
