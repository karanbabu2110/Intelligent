# Capability boundary evolution

## Decision

KAOS starts every capability as a package in the single application. A boundary
is promoted only when current, recorded evidence shows that the next boundary
solves a problem that package-level changes cannot solve at lower cost.

This Story [#986](https://github.com/karanbabu2110/KAOS/issues/986)
decision implements the architecture direction in roadmap
[#814](https://github.com/karanbabu2110/KAOS/issues/814). It is architecture-
specific input to the broader incremental workflow rules in Feature
[#821](https://github.com/karanbabu2110/KAOS/issues/821); it does not complete
that later feature.

## Boundary ladder

Promotions are sequential by default:

```text
package -> Gradle module -> reusable library/repository -> deployed service
```

A review may decide to stay at the current stage. A later stage is not a mark
of maturity, and skipping stages requires direct evidence that intermediate
boundaries cannot address the observed problem.

## Package to Gradle module

Keep a capability as a package unless at least one of these problems is measured
and a module is shown to improve it:

- incompatible compile-time dependencies cannot be isolated with package
  design or dependency cleanup;
- build or test feedback is materially slow, and task-level measurements show
  an independently cacheable/testable project would improve the active loop;
- an enforceable compile-time dependency direction is repeatedly violated and
  package visibility plus tests have proved insufficient;
- a distinct artifact is needed by another current project in the same build.

Before promotion, record build timings or dependency evidence, the classes that
move, the allowed dependency direction, expected developer-loop improvement,
and a rollback plan. A new module remains in the monorepo and process unless
separate evidence justifies another promotion.

## Gradle module to reusable library or repository

A module may become a versioned library only when two or more current consumers
need the same stable contract and independent versioning solves a demonstrated
coordination problem. A separate repository additionally requires evidence such
as:

- an independent release lifecycle that the application repository blocks;
- a real external consumer with access or distribution needs that cannot be
  served safely from the monorepo;
- materially different ownership, licensing, confidentiality, or contribution
  controls;
- repository-scale tooling or history costs measured on the current workflow.

The review must identify consumers, compatibility policy, publishing and
rollback mechanics, vulnerability/update ownership, and the ongoing cost of
cross-repository changes. One consumer or hypothetical reuse is insufficient.

## Library or in-process capability to deployed service

A service boundary requires a current runtime need that cannot be met reliably
in-process, for example:

- independent deployment is required to change one capability without releasing
  the application;
- measured load needs independent scaling with a meaningful resource or cost
  benefit;
- faults must be isolated because current failures have unacceptable process-
  level impact;
- the capability requires incompatible hardware, operating system, runtime, or
  security isolation;
- resilience or geographic placement has a concrete service-level objective
  that an in-process design cannot meet;
- an independently operated consumer requires a network-accessible contract.

Before extraction, define latency and availability budgets, authentication and
authorization, data ownership and consistency, failure and retry semantics,
observability, deployment ownership, local-development impact, contract
compatibility, and rollback. If those costs exceed the evidenced benefit, keep
the capability in-process.

## Non-triggers

None of these alone justify promotion:

- a capability has a distinct name such as AI, RAG, memory, agents, browser, or
  desktop automation;
- KAOS may eventually serve worldwide users or enterprise customers;
- a diagram looks cleaner with more boxes;
- a technology, framework, repository, or microservice would be interesting to
  learn;
- another large organization uses that architecture;
- code might be reused, scaled, secured, or owned independently someday;
- a target folder is large without measured navigation, build, test, dependency,
  or ownership harm;
- an AI coding assistant can generate the additional scaffolding cheaply.

Generated code is not free: every boundary adds decisions, tests, upgrades,
failure modes, documentation, and debugging paths that one developer must own.

## Boundary decision record

Use this lightweight template only when evidence raises a boundary question:

```markdown
# Boundary decision: <capability and proposed promotion>

- Roadmap issue:
- Date:
- Current boundary:
- Proposed boundary:
- Observed problem:
- Evidence and measurements:
- Current consumers and owners:
- Lower-cost package or current-boundary remedies tried:
- Options considered, including no change:
- Expected benefit:
- New build, release, runtime, security, data, and operational costs:
- Validation plan and success threshold:
- Migration and rollback plan:
- Decision: stay / experiment / promote
- Revisit trigger or date:
```

Reject or defer the proposal when the problem is hypothetical, evidence is
missing, a lower-cost remedy is untried, success cannot be measured, ownership
is unclear, or rollback is unsafe.

## Current KAOS evaluation

| Stage | Current evidence | Decision |
| --- | --- | --- |
| Package | One application, one entry point, one startup behavior, one test | Keep |
| Gradle module | No dependency conflict, build isolation need, or second project consumer | Do not create |
| Library/repository | No independent consumer or release lifecycle | Do not create |
| Service | No independent deployment, scale, isolation, hardware, resilience, or network-consumer need | Do not create |

Direct in-process integration therefore remains the current default. These
decisions can change when implementation supplies new evidence; the long-term
KAOS vision is preserved without making future topology a present prerequisite.

## Feature 000.04 acceptance checkpoint

| Criterion | Evidence |
| --- | --- |
| Exactly one application starts | `io.kaos.app.KaosApplication`; canonical `run` succeeds |
| Purpose, location, and run command are clear | [Entry-point decision](single-application-entry-point.md) |
| New capability has an obvious package-first start | [Package-first structure](package-first-application-structure.md) names `io.kaos.ai` |
| Future extraction remains possible | This ladder defines observable promotions and rollback without extracting now |
| Validation and results are recorded | Story documents plus final run, test, build, graph, dependency, source, link, and Git checks |
| Documentation matches current limitations | README and all three Feature 000.04 decisions distinguish bootstrap from product capability |
| Evidence is current and #814-rooted | All decisions link current source and #814 descendant issues |
| Next approved work is explicit | Merge Feature #819, then begin Feature #820 with Story #987 |

## Handoff

Merge the single Feature
[#819](https://github.com/karanbabu2110/KAOS/issues/819) pull request. Then begin
Feature [#820](https://github.com/karanbabu2110/KAOS/issues/820) with Story
[#987](https://github.com/karanbabu2110/KAOS/issues/987). No architecture
extraction is part of this handoff.
