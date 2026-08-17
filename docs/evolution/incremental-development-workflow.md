# One-goal incremental development workflow

## Purpose

KAOS is built by one developer, assisted by AI agents, as one evolving Java
application. Work proceeds through one useful, bounded goal at a time. The
long-term product may become large, modular, distributed, and independently
released, but those boundaries are introduced only when current implementation
evidence justifies their cost.

This workflow is the operating contract for roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814). It is self-contained:
historical issues may provide evidence, but they are not development
requirements.

## Delivery unit

The normal delivery unit is one feature that produces an observable outcome.
It should be small enough to understand, implement, verify, document, and merge
without simultaneously building unrelated future foundations.

Use this hierarchy only when it improves execution:

```text
roadmap -> epic -> feature -> optional story -> task
```

- A feature is the pull-request and release checkpoint.
- Add stories only when a feature contains multiple independently meaningful
  outcomes that need separate acceptance evidence.
- Add direct tasks when the feature only needs a few implementation steps.
- Create stories and tasks just in time for the active feature, not for the
  entire future roadmap.

## One-goal loop

### 1. Select

Select the first incomplete feature in the #814 sequence. Confirm its outcome,
acceptance criteria, non-goals, and current evidence. Mark only the path from
#814 to the current task In Progress. Leave all later work Todo.

### 2. Bound

Write the smallest tasks needed to produce the feature outcome. State what the
increment will not do. Do not add a framework, Gradle module, repository,
service, plugin system, generic platform, or enterprise control merely because
a later capability might need it.

Start a capability as a package in the single application. Follow the
[capability boundary ladder](capability-boundary-evolution.md) only after an
observed problem and measured benefit justify promotion.

### 3. Implement

Implement the thinnest end-to-end behavior that can teach us something or make
KAOS more useful. Prefer real behavior over placeholder architecture. Keep
changes inside the selected task and preserve a working main branch.

An AI agent must inspect current source and issue contracts before editing,
preserve unrelated user changes, and stop when required authority or a product
decision is missing. Generated scaffolding carries the same ownership and
maintenance cost as handwritten code.

### 4. Verify

Run the narrowest relevant check while developing, then the repository's full
current validation before completing the feature. Verification must prove the
claimed behavior and guard against regression; a successful command with no
relevant assertion is not evidence.

The current application baseline is verified with:

```powershell
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --no-daemon
```

Additional checks are added only when the increment introduces corresponding
behavior or risk. The proportional quality and safety rules are defined by
Task [#1042](https://github.com/karanbabu2110/KAOS/issues/1042).

### 5. Record the checkpoint

Before declaring a task complete, record:

- **Outcome:** the behavior or decision now available;
- **Non-goals:** tempting adjacent work deliberately excluded;
- **Evidence:** tests, commands, observed output, source, and issue links;
- **Limitations:** what is incomplete, provisional, or not yet proven;
- **Next checkpoint:** the single next approved task or feature.

Update documentation to describe the implementation that exists, not a
speculative target architecture. A later session must be able to resume from
the README, current issue, and linked checkpoint without reconstructing prior
conversation history.

### 6. Publish the feature

Use one branch and one pull request per feature. Commit each story or direct
task separately with its active issue number, for example:

```text
docs(TASK-000.06.01): publish one-goal delivery workflow #1041
```

Close a task only after its evidence is recorded and set its project status to
Done. When all feature work is complete, run final validation, open the feature
pull request, review it against the feature contract, merge it, delete the
remote and local feature branch, and activate only the next feature.

## Architecture rule

Implementation precedes generalization. Introduce the smallest boundary that
solves the observed problem:

```text
class/package -> Gradle module -> reusable library/repository -> deployed service
```

Staying at an earlier boundary is a valid architectural decision. A worldwide
or enterprise-scale vision does not itself justify current distribution,
independent deployment, or operational complexity. Preserve evolution through
clear contracts, tests, and recorded extraction triggers rather than unused
infrastructure.

## Outcome and non-goals

The outcome of Task
[#1041](https://github.com/karanbabu2110/KAOS/issues/1041) is a repeatable
solo-development loop that an AI agent can execute from current repository and
roadmap evidence.

This task does not define the detailed safety tiers, complete Feature 000.06,
implement a product capability, or extract a module/service. Those remain
separate, explicit checkpoints.

## Evidence, limitations, and next checkpoint

- **Evidence:** the workflow matches the live #814 hierarchy, the
  [execution-project procedure](execution-project.md), the
  [Feature 000.05 integrity proof](backlog-integrity-checkpoint.md), and the
  existing single-application boundary rules.
- **Limitations:** the current application still proves bootstrap behavior
  only; the workflow will be validated end to end in Task #1043.
- **Next checkpoint:** complete Task
  [#1042](https://github.com/karanbabu2110/KAOS/issues/1042), defining
  proportional quality and safety guardrails.
