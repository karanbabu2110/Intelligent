# Incremental development rules checkpoint

## Outcome

Feature [#821](https://github.com/karanbabu2110/KAOS/issues/821)
establishes the operating model for building KAOS one useful goal at a time.
The [one-goal workflow](incremental-development-workflow.md) defines delivery;
the [quality and safety guardrails](proportional-quality-and-safety.md) define
the controls applied to each increment.

Together they preserve the long-term KAOS vision while keeping present work
inside one understandable application and one active feature.

## Completed-feature walkthrough

Feature [#820](https://github.com/karanbabu2110/KAOS/issues/820) was checked
against the new rules:

| Workflow checkpoint | Feature 000.05 evidence |
| --- | --- |
| Select one outcome | Reorganize the GitHub backlog, with #820 as the only active feature |
| Bound the work | Four independently useful stories covered hierarchy, execution project, legacy isolation, and integrity proof |
| Implement before framework | Existing GitHub issues and projects were normalized; no new planning platform or repository was introduced |
| Verify proportionally | Native hierarchy, project membership/fields, historical isolation, repository links, Gradle build/test, and startup were checked |
| Record a resumable checkpoint | The [integrity checkpoint](backlog-integrity-checkpoint.md) records outcome, evidence, limitations, and #821 as the next feature |
| Publish one feature | Four story commits were reviewed and merged through one Feature 000.05 pull request, then the branch was deleted |

The walkthrough can be completed from current #814 descendants and repository
documents. No previous roadmap issue is required.

## Risk walkthrough

The rules were also applied to three representative changes:

| Change | Classification | Result |
| --- | --- | --- |
| Update a roadmap document | Routine | Link, consistency, issue, and diff checks are sufficient |
| Add an in-process RAG experiment over bounded local test data | Guarded | Input/resource bounds, failure tests, dependency/data review, and recovery notes are required |
| Let an agent perform an authenticated browser, desktop, or messaging write | Controlled | Review before implementation, explicit user authority, least privilege, confirmation or dry-run, denial/failure tests, audit evidence, and rollback are required |

This demonstrates that the same workflow scales its controls with present
consequences without forcing all future enterprise infrastructure into every
increment.

## Live roadmap and project evidence

On 2026-08-17, the live roadmap label set and Organization Project 1 were
compared after adding Feature 000.06's direct tasks:

| Check | Result |
| --- | ---: |
| Roadmap-labeled issues | 236 |
| Project 1 issue items | 236 |
| Missing project items | 0 |
| Items outside the roadmap set | 0 |
| Duplicate project issue numbers | 0 |

The native Feature #821 children are exactly #1041, #1042, and #1043. Project
field distributions before final merge are:

- Status: 74 Done, 4 In Progress, 158 Todo;
- Horizon: 79 Now, 15 Next, 142 Later;
- Priority: 6 High, 88 Medium, 142 Low.

The exact active path is `#814 -> #815 -> #821 -> #1043`; Tasks #1041 and
#1042 are closed and Done. Three accidental retry issues (#1044-#1046) were
closed, stripped of evolutionary roadmap labels, and excluded from Project 1;
the canonical tasks remain #1041-#1043.

## Feature 000.06 acceptance map

| Criterion | Evidence |
| --- | --- |
| Rules are discoverable from the current workflow | README links both authoritative rule documents and the current task |
| Every increment records outcome, non-goals, evidence, limitations, and next checkpoint | The one-goal workflow makes all five fields a completion requirement and uses them in each rule document |
| Architecture remains proportional to observed need and risk | The implementation-before-framework rule, boundary ladder, risk levels, and deferred-enterprise list require present evidence |
| Codex can apply the rules without previous issues | Agent authority, one-goal loop, commands, commit format, issue hierarchy, and stop conditions are self-contained |
| Validation is recorded | This walkthrough, live project comparison, link/diff checks, and final application commands record the proof |
| Documentation matches behavior and limitations | Documents state that the application is still a bootstrap and introduce no unimplemented product or enterprise claim |
| Evidence comes from #814 descendants and current artifacts | The workflow, guardrails, source, README, Feature #820 proof, and Tasks #1041-#1043 are all current roadmap evidence |
| Next approved work is explicit | Merge Feature #821, delete its branch, then begin Feature #822 |

## Repository validation

The feature's final validation uses:

```powershell
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --no-daemon
git diff --check
```

Markdown links are resolved relative to each document. The application must
print `KAOS application baseline is running.` and make no product-capability
claim.

## Limitations and non-goals

- These rules guide current development; they do not predict the final KAOS
  topology or prevent a later module, repository, library, plugin, or service.
- Risk classification does not replace a feature-specific threat, privacy,
  legal, or production-readiness review when one is triggered.
- Feature 000.06 changes process documentation and project state only. It does
  not add an AI, RAG, memory, agent, browser, desktop, or developer-assistant
  capability.
- Project automation assists the workflow but explicit hierarchy, field, and
  evidence verification remains required.

## Handoff

Publish and merge the one Feature 000.06 pull request, close Task #1043 and
Feature #821 with final evidence, set both Done, and delete the feature branch.
Then activate Feature
[#822](https://github.com/karanbabu2110/KAOS/issues/822), the Epic 000 exit
criteria and first capability handoff.
