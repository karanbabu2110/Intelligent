# Legacy planning isolation

## Decision

Only descendants of roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814) can define KAOS
development work. Historical issues, project items, documentation, and source
may be inspected as evidence, but they cannot be requirements, dependencies,
completion gates, or sequencing inputs.

[Organization Project 1](https://github.com/orgs/Knowledge-Autonomous-Operating-System/projects/1)
is the sole active execution view. User Project 4, **KAOS - Full Roadmap History
& Reference**, remains accessible as a frozen-input reference collection.

## Contract and documentation audit

On 2026-08-17:

- all 230 issues labeled `roadmap: evolutionary` were scanned for `#number`
  references;
- every referenced issue number belonged to the current #814 set;
- current repository documents were scanned for
  `karanbabu2110/KAOS/issues/<number>` links;
- every issue link resolved to a #814 descendant;
- no active contract or repository document directed implementation to a
  legacy issue or backlog.

Historical language remains in foundation evidence documents where it explains
what was preserved, rejected, or may be reconsidered. Those references are
explicitly non-normative and do not name outside issue dependencies.

## Demonstrated historical-project ambiguity

Before isolation, historical Project 4 had 1,024 preserved items and seven
enabled workflows. New just-in-time #814 tasks were automatically added to it
with `Backlog` status, even though its README said it was not the execution
queue. The issue timeline for tasks #1032 through #1034 recorded their addition
to both Project 1 and Project 4.

That behavior did not change #814 scope, but it created two visible boards for
new work and could make the historical project look executable.

## Isolation applied

The following Project 4 workflows were disabled through GitHub's workflow UI:

- `Auto-add to project`;
- `Auto-add sub-issues to project`.

The live Project API then reported both workflows as `enabled: false`. The five
remaining enabled workflows reflect status, closure, or pull-request events for
already preserved items and do not ingest new issues.

No issue, comment, project item, field, source file, Git commit, or historical
project was deleted, archived, or closed. Project 4 still contains all 1,024
items that existed before isolation.

Its README and short description now identify it as preserved reference only,
remove stale current-work links, point to Project 1, and prohibit adding or
selecting new development work there.

## Reintroducing a historical idea

An older idea may return only through this process:

1. start from a current user or implementation need;
2. inspect historical material as evidence, not authority;
3. validate assumptions against the current single application;
4. choose the smallest present implementation;
5. create a new, complete #814 descendant with its own outcome, scope,
   exclusions, acceptance criteria, validation, and parent link;
6. reference repository paths or immutable commits when preserved code is
   useful;
7. leave the old issue closed or otherwise unchanged and never depend on it.

Copying an old issue body, reopening an issue, or adding it to Project 1 does
not satisfy this process.

## Current boundary

- **Active planning:** #814 native issue hierarchy.
- **Active execution:** organization Project 1.
- **Implementation:** `Knowledge-Autonomous-Operating-System/KAOS` repository.
- **Historical reference:** user Project 4, old issues, and preserved Git
  history.

Story [#990](https://github.com/karanbabu2110/KAOS/issues/990) is next. It will
combine hierarchy, Project 1, and legacy-isolation checks into the final Feature
000.05 integrity proof.
