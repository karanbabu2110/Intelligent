---
name: kaos-evolutionary-development
description: Implement or document KAOS roadmap features, stories, and tasks in the KAOS repository using its one-goal evolutionary workflow and living architecture. Use for changes rooted in roadmap issue 814; do not use for unrelated repositories or read-only general Java questions.
---

# KAOS Evolutionary Development

Deliver one useful KAOS goal at a time with the least process needed to keep the
code, roadmap, and architectural claims trustworthy.

## Work from the current delta

Confirm the checkout and active #814 issue, then inspect only the source and
tests affected by the goal. Read `README.md`, evolution records, or the
architecture website only when the change may make them inaccurate or the
checkpoint is unclear. Reuse evidence already inspected in the same turn; do
not repeat an audit because a later step mentions the same artifact.

Repository code and current test results are implementation truth. Planned
issues preserve intent but do not prove capability. Only #814 descendants are
active development requirements.

## Deliver one feature

- Use one feature branch and one pull request.
- Change Project fields only at real transitions: the active chain when work
  starts and Done after merge.
- Create a child story or task only for a separate outcome, commit, or needed
  acceptance detail. A small concrete feature may use its feature issue.
- Briefly state the intended behavior, boundaries, and likely files before
  implementation. Expand the explanation only when real alternatives exist.
- Start inside the single application and existing package. Add a module,
  library, repository, plugin, worker, event bus, or service only when current
  evidence satisfies `docs/evolution/capability-boundary-evolution.md`.
- Keep each commit independently understandable and include its issue number,
  for example `feat(TASK-002.01.01): connect to local Ollama #123`.
- Never create a tag or release unless the user explicitly requests it.

Before every commit, present:

- every included file and why it changed;
- relevant validation results; and
- the exact commit message.

Wait for explicit approval before committing. After approval, push and open the
single feature PR without adding another approval gate unless the user asks.

## Validate proportionally

- Run focused tests while relevant code is changing.
- For application or build changes, run `clean verifyLocal` once on the exact
  final pre-commit tree. Rerun only if code, build configuration, or fixtures
  change afterward.
- For documentation- or skill-only work, use targeted content checks and
  `git diff --check`; do not run application tests.
- Combine compatible repository and GitHub reads. Do not refetch unchanged
  issue, Project, branch, or PR data.

After merge, compare the verified feature-head tree with merged `main`. If the
trees match, reuse the pre-merge validation and check only checkout/status. Run
`verifyLocal` again when the merge changed the tree or the merged content was
not previously verified.

Rely on PR close keywords, Project automation, and automatic branch deletion.
Inspect final state once and fix only exceptions; do not duplicate completion
comments already recorded in the PR. Finish cleanup, then wait for the user to
continue before activating another feature.

## Update only affected documentation

Change the architecture website only when packages, dependencies, integrations,
runtime/data flow, configuration, security/failure boundaries, deployment, or
architectural status changed. Otherwise leave it untouched. When it changes,
follow `.agents/skills/kaos-architecture-website/SKILL.md`.

Do not edit documentation merely to record activity:

- update `README.md` for product state or usage, not only an active task number;
- update the developer guide for prerequisites, commands, outputs, or
  troubleshooting;
- keep detailed evidence in the feature record and PR;
- update completed-work indexes only when their current summary becomes false
  or at an epic/release checkpoint; and
- check only added or changed local links, then run `git diff --check`.
