# Evolutionary execution project

## Purpose

[Organization Project 1](https://github.com/orgs/Knowledge-Autonomous-Operating-System/projects/1)
is the single execution view for roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814). The issue hierarchy
defines scope; the project makes current state, horizon, and priority filterable.

The project is not a second roadmap. An item cannot become KAOS development
work merely by appearing on a board; it must first be a native #814 descendant.

## Verified membership

On 2026-08-20, live issue and project sets were compared by GitHub issue number:

| Check | Result |
| --- | ---: |
| #814 hierarchy issues carrying `roadmap: evolutionary` | 250 |
| Project 1 items | 250 |
| Missing project items | 0 |
| Items outside the roadmap set | 0 |
| Duplicate issue numbers | 0 |

This includes the fixed 1-roadmap/19-epic/146-feature catalog and the stories
and tasks created just in time through Feature 001.06. See the
[roadmap hierarchy checkpoint](roadmap-hierarchy.md).

## Fields and current distribution

| Field | Options | Verified distribution |
| --- | --- | --- |
| Status | Todo, In Progress, Done | 148 Todo, 3 In Progress, 99 Done |
| Horizon | Now, Next, Later, Vision | 102 Now, 6 Next, 142 Later, 0 Vision |
| Priority | Critical, High, Medium, Low | 0 Critical, 6 High, 102 Medium, 142 Low |

Every one of the 250 items has all three values. The original normalization
found 36 older task
items with blank Horizon and Priority. They were normalized from their single
`horizon: now` and `priority: medium` labels. Two ancestor statuses were also
corrected so the active chain is represented end to end.

## Status rules

- **Todo:** default for every new, open item that is not on the current work
  chain.
- **In Progress:** exactly the current roadmap, epic, feature, optional story,
  and task chain. At this checkpoint: #814, #2, and #842.
- **Done:** the issue is closed as completed and its evidence is recorded.

Closing an issue and setting Done are both required. A project field does not
replace issue state, and issue closure does not replace the project field.

## Horizon rules

- **Now:** active Epic 000 and its prepared descendants.
- **Next:** the next likely application and AI stages.
- **Later:** ordered future capability outcomes that impose no current
  implementation requirement.
- **Vision:** a valid project option, deliberately unused because #814 excludes
  vision-only items from active development.

Horizon labels are the issue-level source used to normalize project values.
Moving an item between horizons requires updating the affected #814 descendant
and its project field together.

## Priority rules

Priority is proportional within a horizon:

- **Critical:** immediate safety, security, data-loss, or release-blocking work;
- **High:** important stage or risk-reduction outcome;
- **Medium:** normal current delivery work;
- **Low:** ordered later work with no current urgency.

Priority does not override one-goal-at-a-time sequencing. A Later/Low feature
does not become active merely because it is interesting.

## Enabled workflows

The project exposes six enabled workflows:

- Item added to project;
- Auto-add sub-issues to project;
- Item closed;
- Auto-close issue;
- Pull request linked to issue;
- Pull request merged.

Automation is assistance, not proof. GitHub's API exposes workflow name and
enabled state but not enough configuration detail here to treat field behavior
as self-verifying. New descendants are therefore explicitly normalized and the
full set is re-audited.

## Just-in-time item procedure

For each new story or task:

1. create the issue with exactly one roadmap, horizon, priority, and type label;
2. attach it through the native parent/sub-issue relationship;
3. ensure it appears exactly once in Project 1;
4. set Status to Todo, then set only the selected item to In Progress;
5. copy Horizon and Priority from the issue labels into project fields;
6. verify the current and next items are unambiguous;
7. on completion, comment with evidence, close the issue, and set Done.

## Current and next work

The active path is #814 -> #3 -> #845. Epic #2, all six of its features, and
their direct tasks are closed and Done. Feature
[#845](https://github.com/karanbabu2110/KAOS/issues/845) is the one approved
goal; its stories or direct tasks are created just in time after its contract is
reviewed.

No historical project is needed to plan or execute this chain. The
[legacy-planning isolation](legacy-planning-isolation.md) keeps historical
automation and references from creating active-scope ambiguity.

## Repeatable audit

1. retrieve all issues labeled `roadmap: evolutionary`;
2. retrieve all Project 1 items with Status, Horizon, Priority, and labels;
3. compare issue-number sets for missing, outside, and duplicate items;
4. derive expected fields from issue state, the current chain, and the single
   horizon/priority labels;
5. fail on a blank or mismatch and normalize only the demonstrated differences;
6. retrieve the project again and require zero differences.

This check is intentionally independent from historical project data.
