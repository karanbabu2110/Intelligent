# Evolutionary roadmap hierarchy

## Authority

[Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814) is the sole
KAOS development root. Native GitHub parent/sub-issue relationships, not issue
number sequence or a historical project, define membership in this hierarchy.

Some evolutionary epics reuse low GitHub issue numbers because their issue
bodies and planning meaning were replaced and they were attached beneath #814.
Their `EVOLUTION-EPIC-nnn` identity and native parent link are authoritative;
the numeric GitHub issue identifier alone does not imply legacy scope.

## Verified catalog

Live GitHub state was verified on 2026-08-17:

- 1 roadmap root;
- 19 direct evolutionary epics;
- 146 features attached to their approved epics;
- 17 just-in-time stories under active or completed Epic 000 features;
- 41 tasks under those stories;
- 224 unique issues in the current #814 hierarchy;
- 224 issues carrying the `roadmap: evolutionary` label;
- zero roadmap-labeled issues outside the #814 graph;
- zero #814 descendants missing the roadmap label.

| Epic | GitHub issue | Features | First feature | Last feature |
| --- | ---: | ---: | --- | --- |
| EVOLUTION-EPIC-000 | #815 | 7 | 000.01 (#816) | 000.07 (#822) |
| EVOLUTION-EPIC-001 | #2 | 6 | 001.01 (#839) | 001.06 (#843) |
| EVOLUTION-EPIC-002 | #3 | 7 | 002.01 (#845) | 002.07 (#851) |
| EVOLUTION-EPIC-003 | #9 | 6 | 003.01 (#852) | 003.06 (#857) |
| EVOLUTION-EPIC-004 | #823 | 7 | 004.01 (#859) | 004.07 (#864) |
| EVOLUTION-EPIC-005 | #11 | 9 | 005.01 (#865) | 005.09 (#873) |
| EVOLUTION-EPIC-006 | #10 | 9 | 006.01 (#875) | 006.09 (#882) |
| EVOLUTION-EPIC-007 | #15 | 9 | 007.01 (#884) | 007.09 (#891) |
| EVOLUTION-EPIC-008 | #63 | 8 | 008.01 (#892) | 008.08 (#898) |
| EVOLUTION-EPIC-009 | #53 | 9 | 009.01 (#900) | 009.09 (#908) |
| EVOLUTION-EPIC-010 | #26 | 8 | 010.01 (#910) | 010.08 (#916) |
| EVOLUTION-EPIC-011 | #23 | 8 | 011.01 (#918) | 011.08 (#924) |
| EVOLUTION-EPIC-012 | #37 | 9 | 012.01 (#925) | 012.09 (#933) |
| EVOLUTION-EPIC-013 | #51 | 7 | 013.01 (#934) | 013.07 (#940) |
| EVOLUTION-EPIC-014 | #824 | 7 | 014.01 (#941) | 014.07 (#947) |
| EVOLUTION-EPIC-015 | #825 | 7 | 015.01 (#948) | 015.07 (#954) |
| EVOLUTION-EPIC-016 | #826 | 7 | 016.01 (#955) | 016.07 (#961) |
| EVOLUTION-EPIC-017 | #60 | 8 | 017.01 (#962) | 017.08 (#969) |
| EVOLUTION-EPIC-018 | #827 | 8 | 018.01 (#970) | 018.08 (#977) |
| **Total** |  | **146** |  |  |

The feature code is stable planning identity. GitHub issue numbers in the table
are navigation evidence and may not be numerically ordered.

## Level responsibilities

```text
roadmap -> epic -> feature -> optional story -> task
                              `-------------> task
```

- **Roadmap:** preserves the ordered long-term KAOS outcome inventory and the
  evolutionary development rules.
- **Epic:** groups a coherent product or development stage and defines its exit
  outcome.
- **Feature:** is the default review and pull-request boundary. It must produce
  one bounded, demonstrable outcome.
- **Story:** is optional. Use it only when a feature has multiple independently
  implementable and verifiable outcomes.
- **Task:** is the smallest current implementation, decision, or verification
  step. Create it only when its parent enters active work.

## Decomposition rules

1. Do not create a one-story wrapper merely to make every feature look alike.
2. If a feature is one coherent outcome, attach just-in-time tasks directly to
   the active feature.
3. If stories are justified, attach all implementation tasks to the active
   story; do not mix feature-level and story-level tasks for the same work.
4. Do not pre-create tasks for later features or stories.
5. Each story must explain its independent outcome and why a separate delivery
   boundary helps.
6. One feature is active at a time. Its stories normally proceed in numeric
   order, and one feature-level pull request integrates their commits.
7. After a feature PR merges, close its descendants with evidence, mark them
   Done, delete the feature branch, and only then activate the next feature.

## Current navigation path

```text
#814 Roadmap
  -> #815 EVOLUTION-EPIC-000
      -> #820 EVOLUTION-FEATURE-000.05
          -> #987 EVOLUTION-STORY-000.05.01
```

Story #987 is captured by this checkpoint. Story
[#988](https://github.com/karanbabu2110/KAOS/issues/988) is next and will verify
the organization execution project. No issue outside the path rooted at #814 is
required to identify or understand that work.

## Repeatable verification

Use GitHub's native GraphQL `subIssues` connection in two bounded passes:

1. query #814 through epic and feature depth, then verify 19 epics, 146
   features, approved per-epic counts, unique issue IDs, and naming patterns;
2. query each Epic 000 feature through story/task depth, combine those nodes
   with the catalog, and compare the resulting set with all issues labeled
   `roadmap: evolutionary`.

The bounded passes avoid GitHub's GraphQL node-complexity limit while proving
both the fixed catalog and the just-in-time descendants. Project-field
verification belongs to Story #988; legacy-isolation verification belongs to
Story #989; the final combined integrity procedure belongs to Story #990.

## Limitations

- Counts change when an active feature legitimately creates or closes stories
  and tasks; the fixed invariant is 19 epics and 146 features unless #814 is
  explicitly revised.
- A label is not a substitute for a native parent link. Set comparison is used
  to detect both labeled outsiders and unlabeled descendants.
- This artifact records the issue hierarchy only. It does not prove project
  field configuration or legacy isolation, which are the next two stories.
