---
name: kaos-evolutionary-development
description: Implement or document KAOS roadmap features, stories, and tasks in the KAOS repository using its one-goal evolutionary workflow and living architecture. Use for changes rooted in roadmap issue 814; do not use for unrelated repositories or read-only general Java questions.
---

# KAOS Evolutionary Development

Develop one useful KAOS goal at a time while keeping the repository's current
state, evidence, and architecture understandable.

## Orient before changing KAOS

Read the active checkpoint in `README.md`, the relevant #814 roadmap issue, the
code affected by the goal, and `ui/architecture/index.html`. Treat repository
code and current verification results as implementation truth. Planned issues
preserve intent but do not prove that a capability exists.

Use only issues under
[KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
as active development requirements. Preserve older material only as historical
evidence when it helps the current goal.

## Implement one goal

- Start a capability as a package in the single application.
- Add a Gradle module, library, repository, plugin, or service only when current
  evidence satisfies the extraction rules in
  `docs/evolution/capability-boundary-evolution.md`.
- Create stories only when a feature needs independently understandable pieces;
  otherwise use direct tasks.
- Keep all stories and tasks for one feature on its feature branch and publish
  one pull request for the completed feature.
- Include the story or task number and GitHub issue at the end of each commit
  message, for example:
  `feat(STORY-002.01.01): connect to local Ollama #<issue>`.
- Do not create a Git tag or GitHub release unless the user explicitly asks.

Verify in proportion to the change. For application changes, use the focused
tests during development and run the repository's complete `verifyLocal`
checkpoint before completing the feature.

## Maintain the living architecture

Update `ui/architecture/` in the same feature pull request whenever
the work changes any of these:

- packages, modules, repositories, plugins, processes, or deployment units;
- production dependencies or external integrations;
- runtime request, command, or data flow;
- configuration, security, data ownership, or failure boundaries;
- the status of a capability or architectural boundary.

Keep the diagram evidence-based:

- show implemented and verified elements as implemented;
- show the active approved addition as next work until its evidence exists;
- show longer-term capabilities as candidates, not promised topology;
- remove or revise stale elements instead of accumulating historical states;
- link architectural claims to source, build, decision, or roadmap evidence;
- update the verification date only after comparing the page with the code;
- keep the page self-contained unless the repository deliberately adopts a
  documented architecture-rendering dependency.

If a feature does not change architecture, explicitly check the page and leave
it unchanged. Never update the visual merely to suggest progress.

When an architecture update is required, read and follow
`.agents/skills/kaos-architecture-website/SKILL.md` for the website-specific
evidence, organization, accessibility, growth, and visual-validation workflow.

## Keep project entry points readable

Inspect documentation affected by the current goal, but edit only files whose
content would otherwise become inaccurate, incomplete, or misleading. Running
this skill does not by itself require a README, developer-guide, architecture,
evidence, or roadmap-history change. Do not touch a file merely to refresh it,
record that it was checked, or create documentation churn.

Keep `README.md` focused on the current state, essential usage, active roadmap
chain, architecture link, and next checkpoint. Update it only when one of those
entry-point facts changes.

Keep `docs/development/developer-guide.md` as the practical source of truth for
setting up, running, testing, verifying, and troubleshooting the current
application. Update it only when the feature changes prerequisites, commands,
Gradle tasks, test scopes, generated outputs, developer-facing configuration,
troubleshooting, or the delivery workflow. Keep evidence-heavy implementation
history in the relevant `docs/evolution` record rather than expanding the guide.

Update `docs/evolution/completed-work-and-evidence.md` only when a roadmap item
is completed or its verified-evidence link changes. When an epic completes, move
its feature, story, task, and verified-evidence links there from the README.

Create or update a feature-specific `docs/evolution` record only when the active
issue requires durable implementation evidence that is not already clear from
source, tests, the developer guide, or the architecture page.

Before handing off a documentation change, verify local links, run
`git diff --check`, and report whether application tests were or were not needed.
