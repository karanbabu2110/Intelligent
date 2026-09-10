# Agent Goal Representation

Feature [009.02](https://github.com/karanbabu2110/KAOS/issues/901)
introduces the first runtime value for the
[bounded agent use case](bounded-agent-use-case.md): one immutable user
objective represented by `AgentGoal`.

## Implemented contract

`AgentGoal` contains exactly:

- a caller-supplied non-null `UUID` identity; and
- one non-null, nonblank objective of at most 4,096 Unicode code points.

The objective preserves the user's text exactly, including ordinary leading or
trailing whitespace, because silently rewriting the goal could change intent.
Line breaks and tabs are accepted, while other ISO control characters are
rejected. The ceiling matches the existing ordinary Ollama prompt ceiling and
prevents an agent goal from beginning with an input that cannot fit that
boundary.

The record is immutable. It has no `createdAt`, priority, hierarchy,
dependencies, tool name, arguments, approval, or execution state. Creating it
does not load configuration, call a model, resolve a tool, access a file, use
the network, or persist the objective. A later agent-run owner may contain one
goal, but the goal value is not a run manager.

## Privacy and authority

Possessing an `AgentGoal` grants no tool or execution authority. Later planning
may inspect its objective to propose evidence steps, but every plan and tool
operation remains independently validated and approved.

The record overrides its generic string representation to show only the goal
identity and objective code-point count. It does not copy private objective
text into routine diagnostics. Explicit application behavior may display the
goal to its user when a later feature demonstrates that need.

## Validation

`AgentGoalTest` deterministically proves exact Unicode preservation, the exact
code-point boundary, rejection of a missing identity, null/blank/oversized or
unsafe objectives, and content-free generic diagnostics. The tests require no
Ollama, SearXNG, public internet, filesystem fixture, or database.

## Deliberate limits and handoff

This feature does not add a command or complete an agent run. It adds no goal
factory, repository, persistence, timestamp, mutable lifecycle, management
service, model schema, tool integration, or approval behavior.

The next ordered feature is
[009.03 - Simple Step Planning](https://github.com/karanbabu2110/KAOS/issues/902).
It should introduce one small plan and step representation plus deterministic
validation for the four evidence decisions, without giving the goal or model
execution authority.
