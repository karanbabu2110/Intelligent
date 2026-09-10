# Freshness Policy Hardening

Feature [009.10](https://github.com/karanbabu2110/KAOS/issues/1092) hardens
the completed Epic 009 planner without reopening the epic or introducing a
classifier framework. `FreshnessPolicy` independently assesses the bounded
`AgentGoal` before execution, and the resulting `FreshnessRequirement` is
retained on the validated `AgentPlan`.

## Three-level decision

| Requirement | Meaning and enforcement |
| --- | --- |
| `REQUIRED` | Explicit recency or current-state wording makes public evidence necessary. `AgentPlanner` rejects any structurally valid plan whose information need is not `CURRENT_PUBLIC_EVIDENCE` or `MIXED_EVIDENCE`. |
| `RECOMMENDED` | A paired recommendation intent and technology/product context, or a narrowly phrased security, purchase, public-office, legal, or schedule question may change over time. The plan is accepted even without search, while the assessment remains observable on `AgentPlan`. |
| `NOT_REQUIRED` | No deterministic current-evidence signal is present. Stable internal or local-only plans remain valid. |

Hard-required phrases include explicit terms such as `latest`, `currently`,
`today`, `recent`, `right now`, `as of`, `this week`, and `this month`, plus
bounded combinations such as `current price`, `current version`, `best current`,
`current security guidance`, and `available now`. Softer rules require context
combinations rather than isolated domain words. Consequently `semantic
versioning`, `price elasticity`, `security token`, and `event sourcing` do not
trigger freshness by themselves.

The rules are deliberately small, deterministic, locale-stable, and
content-free in failures. Rejection uses `FRESHNESS_REQUIRED`; neither the
exception nor generic diagnostics contains the goal. The model still proposes
the evidence shape, but cannot override a `REQUIRED` policy assessment.

## Empty search evidence

A completed `web_search` tool attempt is current evidence only when its bounded
`WebSearchResult` contains at least one entry. An empty successful response is
recorded as a successful tool attempt, then stops the agent before synthesis
with `CURRENT_EVIDENCE_UNAVAILABLE`. The terminal `AgentResult` reports
`REQUIRED_BUT_UNAVAILABLE` and carries no answer or source.

## Preserved limits

The change adds no tool, retry, fallback, replan, persistence, parallelism,
background work, sub-agent, browser behavior, service, strategy hierarchy, NLP
pipeline, embedding classifier, or rule engine. One goal, one plan, three total
steps, two tool steps, fixed file/search order, and independent Epic 008
approval remain unchanged.

## Limitations

This is conservative phrase/context matching, not general language
understanding. It can miss paraphrases and can classify unusual uses of an
explicit recency phrase as required. `RECOMMENDED` is intentionally
nonblocking; evaluation can observe model underuse of search without silently
expanding tool authority or changing a validated plan.

## Verification

The forced agent-focused command executed 81 tests across nine suites with zero
failures, errors, or skips. The clean repository checkpoint executed all 11
`verifyLocal` tasks and 524 tests across 70 suites: 520 passed, zero failed or
errored, and four existing platform-specific symlink scenarios were skipped.
The living architecture rendered without page-level overflow at desktop and
mobile widths, emitted no console warning or error, retained exactly one current
feature marker, and resolved all 245 links and eight local anchors.
