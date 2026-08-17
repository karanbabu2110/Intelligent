# Backlog integrity checkpoint

## Outcome

Feature [#820](https://github.com/karanbabu2110/KAOS/issues/820)
provides one complete, navigable, and operational #814-only development backlog.
It combines the native hierarchy, organization execution project, and legacy
isolation rules without deleting historical evidence.

## Verified state

The final Story [#990](https://github.com/karanbabu2110/KAOS/issues/990)
proof ran against live GitHub and repository state on 2026-08-17.

### Native hierarchy

| Level | Count |
| --- | ---: |
| Roadmap root | 1 |
| Direct epics | 19 |
| Features | 146 |
| Epic 000 stories | 17 |
| Story tasks | 50 |
| **Unique #814 nodes** | **233** |

The 19 per-epic feature counts are `7, 6, 7, 6, 7, 9, 9, 9, 8, 9, 8,
8, 9, 7, 7, 7, 7, 8, 8`, totaling 146. Epic and feature naming patterns are
valid, and every roadmap-labeled issue is found in the native parent graph.

### Organization Project 1

| Check | Result |
| --- | ---: |
| Roadmap-labeled issues | 233 |
| Project items | 233 |
| Missing items | 0 |
| Outside items | 0 |
| Duplicate issue numbers | 0 |
| Blank or mismatched fields | 0 |
| Issue references outside #814 | 0 |

Field distributions at the checkpoint:

- Status: 67 Done, 5 In Progress, 161 Todo;
- Horizon: 76 Now, 15 Next, 142 Later;
- Priority: 6 High, 85 Medium, 142 Low.

The exact In Progress chain is:

```text
#814 -> #815 -> #820 -> #990 -> #1038
```

Task #1039 is next, followed by #1040. After the feature merges, Feature #821
is the first incomplete Epic 000 feature.

### Historical isolation

- User Project 4 retains all 1,024 historical items.
- `Auto-add to project` is disabled.
- `Auto-add sub-issues to project` is disabled.
- New tasks #1038, #1039, and #1040 are absent from Project 4.
- No historical issue, comment, item, project, or Git evidence was deleted,
  archived, or closed for isolation.

### Application regression

```powershell
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --no-daemon
```

The build and focused test passed, and the application printed
`KAOS application baseline is running.` The backlog work did not change the
single-application runtime.

## Repeatable integrity procedure

1. Query #814 with native `subIssues` through epic and feature depth.
2. Query Epic 000 features through story and task depth in bounded calls.
3. Require 19 epics, 146 features, approved per-epic counts, valid naming, and
   unique issue numbers.
4. Retrieve every issue labeled `roadmap: evolutionary` and every Project 1
   item.
5. Compare the three issue-number sets: native hierarchy, roadmap label, and
   execution project.
6. Fail on a missing, outside, duplicate, or unlabeled node.
7. For each item, derive expected Status from issue state and the current chain;
   derive Horizon and Priority from the issue's single labels.
8. Fail on a blank or mismatched field.
9. Scan every roadmap body for `#number` references and require each reference
   to belong to the same #814 set.
10. Query historical Project 4 workflows and require both ingestion workflows
    disabled.
11. Verify newly created descendants are absent from Project 4.
12. Run repository link, diff, application, test, build, and run checks.

The fixed catalog and current just-in-time counts are deliberately checked
separately. This avoids GitHub GraphQL's deep-query node-complexity limit.

## Operating workflow

1. Work on the first incomplete feature only.
2. Create stories only when the feature contains independently valuable
   outcomes; otherwise attach tasks directly to the feature.
3. Create tasks just in time under the active parent.
4. Add each new descendant to Project 1 and explicitly set Status, Horizon, and
   Priority.
5. Keep only the roadmap-to-current-task path In Progress.
6. Commit each story or direct feature task with its roadmap issue number.
7. Open one pull request for the complete feature.
8. After merge, close descendants with evidence, move them Done, delete the
   feature branch, and activate the next feature.
9. Repeat the integrity procedure after roadmap mutations.

## Feature 000.05 acceptance map

| Criterion | Evidence |
| --- | --- |
| Every development issue traces to #814 | Native hierarchy, label set, and project set contain the same 233 issue numbers |
| Execution project contains the intended hierarchy | Project 1 has exact membership and complete Status, Horizon, and Priority values |
| Previous issues are not requirements or dependencies | All 233 contracts have zero issue references outside #814; historical ingestion is disabled |
| Current and next work are unambiguous | Current chain ends at #1038; #1039 and #1040 finish the feature; #821 follows merge |
| Validation commands and results are recorded | This checkpoint records hierarchy, project, isolation, repository, and application proofs |
| Documentation matches behavior and limitations | README, project READMEs, and four Feature 000.05 records describe live state |
| Evidence is current and self-contained | All required planning evidence comes from #814 descendants, Project 1, and current repository artifacts |
| Next approved work is explicit | Merge #820, then activate #821 and create its direct tasks just in time |

## Limitations

- Story and task counts grow only when current work justifies them; 19 epics and
  146 features are the fixed catalog unless #814 is explicitly revised.
- Project workflows assist but do not replace explicit field normalization and
  integrity verification.
- Historical Project 4 still contains evolutionary migration records among its
  1,024 preserved items; its disabled ingestion and reference-only README, not
  deletion, provide isolation.
- GitHub GraphQL rate and node-complexity limits require bounded queries.

## Handoff

Merge the one Feature 000.05 pull request and delete its branch. Then activate
Feature [#821](https://github.com/karanbabu2110/KAOS/issues/821), **Establish
Incremental Development Rules**. It has one coherent outcome, so its tasks will
be created directly under the feature rather than beneath a one-story wrapper.
