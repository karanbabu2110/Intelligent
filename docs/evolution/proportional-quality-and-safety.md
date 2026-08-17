# Proportional quality and safety guardrails

## Principle

Every KAOS increment has a non-negotiable quality floor. Additional controls
are added in proportion to the behavior and risk introduced by the current
goal, not the eventual size of the product. Future enterprise controls remain
valid options, but they do not become present dependencies without evidence.

These guardrails supplement the
[one-goal workflow](incremental-development-workflow.md) for Feature
[#821](https://github.com/karanbabu2110/KAOS/issues/821).

## Quality floor for every increment

Before a task is completed:

1. trace the change to one active #814 descendant and state the outcome and
   non-goals;
2. inspect the current implementation and preserve unrelated work;
3. add or update the narrowest meaningful automated test when behavior changes;
4. run relevant focused checks and the current repository regression checks;
5. document actual behavior, evidence, limitations, and the next checkpoint;
6. keep secrets, personal data, credentials, generated output, and machine-
   local configuration out of commits;
7. review the diff for unintended scope, destructive behavior, and dependency
   additions;
8. keep main releasable and provide a clear way to revert the increment.

Documentation-only decisions require link, consistency, and diff validation.
Executable changes require behavior assertions; compilation alone is not
sufficient. A task cannot be marked Done merely because code was generated.

## Risk levels

Use the highest applicable level. Record the selected level and why whenever a
change is above Routine.

| Level | Typical change | Required controls in addition to the quality floor |
| --- | --- | --- |
| Routine | Local documentation, reversible refactor, deterministic in-memory behavior | Focused validation and normal feature review |
| Guarded | New dependency, configuration, persistence, network read, user-provided content, concurrency, or resource-intensive AI call | Test failure paths, validate inputs/configuration, define timeouts and resource bounds, review dependency/security impact, and document recovery |
| Controlled | Credentials, private data, authentication/authorization, network write, browser/desktop action, deletion, migration, payment, public release, or other externally visible/irreversible effect | Complete a safety review before implementation, require explicit user authority for the effect, use least privilege and safe defaults, provide dry-run/confirmation or rollback where possible, test denial/failure paths, and record operational evidence |

If classification is uncertain, use the higher level until evidence supports a
lower one.

## Mandatory early-review triggers

Pause before implementation or execution when the selected goal introduces or
changes any of these:

- authentication, authorization, identity, permissions, secrets, tokens, or
  cryptographic material;
- collection, storage, retrieval, transmission, logging, or deletion of
  personal, confidential, regulated, or user-owned data;
- a persistent schema, destructive command, bulk mutation, migration, or
  operation whose rollback is unclear;
- an email, message, purchase, deployment, publication, browser/desktop action,
  or API write that affects an external system or person;
- executable tool use, untrusted content, prompt/tool injection exposure,
  filesystem reach, or dynamic code/process execution;
- a network-facing interface, plugin boundary, independently deployed service,
  or new trust boundary;
- a legal, financial, health, safety, or compliance claim;
- substantial cost, rate-limit, latency, availability, or resource exposure.

The review records the assets and people affected, data flow, authority,
threat/failure cases, least-privilege boundary, validation, observability,
recovery, and the decision to proceed, narrow, or defer.

## AI-agent authority

An AI agent may perform read-only inspection and normal reversible edits inside
the selected repository and issue scope. It may run relevant local checks,
create the approved feature branch, commit task-scoped changes, and publish the
approved feature workflow when the user has granted that authority.

The agent must:

- tie every material change to the active issue and acceptance criteria;
- distinguish observed evidence from assumptions;
- avoid broad cleanup and preserve unrelated user changes;
- show exact targets before a destructive operation;
- never expose secrets or silently weaken a safety control;
- stop when credentials, product policy, affected data, destination, cost,
  external side effect, or rollback authority is unknown;
- report partial or failed validation accurately.

Prior authority for one feature or operation does not automatically authorize a
materially different external effect. Cheap generation does not justify extra
architecture, dependencies, or maintenance surface.

## Proportional engineering decisions

### Tests

Add tests for behavior introduced now. Prefer one useful end-to-end or boundary
test plus focused unit tests over a speculative test framework. Add performance,
load, resilience, compatibility, or security suites when the active change
creates those risks or a measured problem establishes a threshold.

### Documentation

Document how to run, verify, recover, and continue current behavior. Record a
small decision when evidence changes a boundary. Do not pre-write operating
manuals, service contracts, governance catalogs, or standards for components
that do not exist.

### Observability and recovery

For local deterministic behavior, test output and a clear error may suffice.
Add structured logs, metrics, traces, alerts, backups, migrations, idempotency,
or rollback automation when persistence, external actions, deployment, or an
operational objective makes them necessary.

### Architecture and dependencies

Keep the single application and package boundary by default. New libraries,
modules, repositories, and services require a present use case, comparison with
the current boundary, ownership, validation, and rollback. Apply the
[boundary evolution rules](capability-boundary-evolution.md).

## Representative application

| Change | Level | Proportional response |
| --- | --- | --- |
| Clarify the current workflow document | Routine | Link check, content review, diff check; no security platform or service required |
| Add an in-process RAG experiment reading a local test corpus | Guarded | Bound file access and input size, test parsing/retrieval failures, record model/data assumptions; no distributed vector service until evidence requires it |
| Let an agent send email or control a desktop using stored credentials | Controlled | Explicit user authority and destination, least-privilege credentials, confirmation/dry-run where possible, audit evidence, failure/rollback handling, and adversarial input review before release |

The controls differ because current consequences differ, while the quality
floor remains constant.

## Deferred enterprise capabilities

The following are not rejected; they are deferred until an active capability,
risk, operational objective, consumer, or regulation requires them:

- organization-wide governance and approval systems;
- universal plugin, policy, event, or service platforms;
- multi-repository publishing and compatibility infrastructure;
- production-grade distributed observability and orchestration;
- global scale, multi-region, disaster-recovery, and formal compliance systems;
- generalized extension points for hypothetical consumers.

Deferral must not be used to bypass a current Controlled risk. Conversely, a
future enterprise ambition is not evidence that all enterprise controls are
needed for a local bootstrap or experiment.

## Outcome, non-goals, evidence, and next checkpoint

- **Outcome:** Task
  [#1042](https://github.com/karanbabu2110/KAOS/issues/1042) establishes one
  mandatory quality floor, three actionable risk levels, early-review triggers,
  and explicit AI-agent authority boundaries.
- **Non-goals:** no security framework, policy engine, deployment platform,
  compliance certification, or new runtime dependency is introduced.
- **Evidence:** the representative Routine, Guarded, and Controlled changes can
  be classified and assigned concrete controls without changing the current
  single-application architecture.
- **Limitations:** these are development guardrails, not a substitute for a
  feature-specific threat model, legal review, or production readiness review
  when one is triggered.
- **Next checkpoint:** Task
  [#1043](https://github.com/karanbabu2110/KAOS/issues/1043) validates both
  Feature 000.06 rules against a completed feature and the live repository.
