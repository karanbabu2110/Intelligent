# Active project and capability classification

## Purpose

This record assigns one present-state classification to every build surface in
the [registered project and capability surface inventory](registered-project-capability-inventory.md).
It is the authoritative result of Story 000.02.02 under roadmap #814.

Classification is based on repository evidence at immutable baseline
`b9bd26dee098ef338286080042ba51f614804079`, not on project names, old planning
issues, or future intent. Nothing in this record adopts historical source into
the organization repository.

## Classification rules

| Classification | Required evidence |
| --- | --- |
| **Operational now** | Adopted in the current organization repository, produces a demonstrated user or system outcome, and has current verification |
| **Partially operational** | Contains executable or implemented behavior, but the named outcome is incomplete, unverified, or missing an important consumer-facing surface |
| **Proven reusable foundation** | Contains implemented foundation or build behavior with tests or historical execution evidence, but is not a current product capability or adopted dependency |
| **Scaffold only** | Contains registration, a build descriptor, documentation, or a name without implementation source proving the named behavior |
| **Future hypothesis** | Describes a possible capability without a registered implementation surface that can be assessed today |
| **Extraction candidate** | Has current evidence of independent reuse, lifecycle, ownership, deployment, isolation, or scaling pressure that may justify a stronger boundary |

The categories are mutually exclusive for this inventory. A promising name is
not enough to move a surface out of **Scaffold only**. Historical execution is
not enough for **Operational now** because the organization repository has not
adopted the historical build.

## Classification summary

| Classification | Count | Result |
| --- | ---: | --- |
| Operational now | 0 | The organization repository contains documentation only |
| Partially operational | 4 | Four Spring Boot launchers exist, but none implements its named channel outcome |
| Proven reusable foundation | 4 | Root build, included build logic, configuration, and observability have real implementation evidence |
| Scaffold only | 40 | Ten README/build-only shared projects and thirty build-only modules |
| Future hypothesis | 0 | Future roadmap capabilities are not duplicated as build-surface entries |
| Extraction candidate | 0 | No current independent consumer, lifecycle, ownership, deployment, isolation, or scale evidence exists |
| **Total** | **48** | Root, 46 registered subprojects, and the `build-logic` included build |

## Authoritative build-surface classifications

Every inventoried build surface appears exactly once.

| Build surface | Classification | Evidence and rationale |
| --- | --- | --- |
| `:` | Proven reusable foundation | Root build defines real lifecycle, reporting, governance, and developer tasks with historical successful-build evidence; none is adopted as current application behavior |
| `build-logic` | Proven reusable foundation | Separate included build has 20 tracked source files implementing convention plugins and build tasks; it is infrastructure rather than product behavior |
| `:applications:kaos-server` | Partially operational | Spring Boot main class and declared dependencies exist, but there is no controller, route, test, or demonstrated server outcome |
| `:applications:kaos-desktop` | Partially operational | Spring Boot main class exists, but no desktop UI code, test, or demonstrated desktop outcome exists |
| `:applications:kaos-cli` | Partially operational | Spring Boot main class exists, but no command implementation, runner, test, or demonstrated CLI outcome exists |
| `:applications:kaos-web` | Partially operational | Spring Boot main class exists, but no controller, route, web UI, test, or demonstrated web outcome exists |
| `:shared:documentation` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:events` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:foundation` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:kernel` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:mapping` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:platform` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:security` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:serialization` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:testing` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:shared:validation` | Scaffold only | README and build descriptor exist; no implementation or test source exists |
| `:modules:platform:platform-common` | Scaffold only | Build descriptor only; no implementation, test, or project dependency exists |
| `:modules:platform:platform-core` | Scaffold only | Build descriptor only; no implementation, test, or project dependency exists |
| `:modules:platform:platform-events` | Scaffold only | Build descriptor only; no implementation, test, or project dependency exists |
| `:modules:platform:platform-config` | Proven reusable foundation | 19 Java types, auto-configuration metadata, two tests, and implemented properties, validation, secret, lifecycle, and diagnostic behavior exist |
| `:modules:platform:platform-observability` | Proven reusable foundation | 11 Java types, logging resources, three tests, and implemented correlation, audit, structured logging, diagnostic, and health behavior exist |
| `:modules:ai:ai-chat` | Scaffold only | Build descriptor only; no chat implementation, test, or dependency exists |
| `:modules:ai:ai-memory` | Scaffold only | Build descriptor only; no memory implementation, test, or dependency exists |
| `:modules:ai:ai-context` | Scaffold only | Build descriptor only; no context implementation, test, or dependency exists |
| `:modules:ai:ai-rag` | Scaffold only | Build descriptor only; no RAG implementation, test, or dependency exists |
| `:modules:ai:ai-reasoning` | Scaffold only | Build descriptor only; no reasoning implementation, test, or dependency exists |
| `:modules:ai:ai-planning` | Scaffold only | Build descriptor only; no planning implementation, test, or dependency exists |
| `:modules:ai:ai-agents` | Scaffold only | Build descriptor only; no agent implementation, test, or dependency exists |
| `:modules:ai:ai-tools` | Scaffold only | Build descriptor only; no tool implementation, test, or dependency exists |
| `:modules:ai:ai-models` | Scaffold only | Build descriptor only; no model integration, test, or dependency exists |
| `:modules:automation:automation-desktop` | Scaffold only | Build descriptor only; no desktop automation implementation, test, or dependency exists |
| `:modules:automation:automation-browser` | Scaffold only | Build descriptor only; no browser automation implementation, test, or dependency exists |
| `:modules:automation:automation-workflow` | Scaffold only | Build descriptor only; no workflow implementation, test, or dependency exists |
| `:modules:automation:automation-scheduler` | Scaffold only | Build descriptor only; no scheduler implementation, test, or dependency exists |
| `:modules:integrations:integration-email` | Scaffold only | Build descriptor only; no email implementation, test, or dependency exists |
| `:modules:integrations:integration-calendar` | Scaffold only | Build descriptor only; no calendar implementation, test, or dependency exists |
| `:modules:integrations:integration-storage` | Scaffold only | Build descriptor only; no storage integration, test, or dependency exists |
| `:modules:integrations:integration-slack` | Scaffold only | Build descriptor only; no Slack implementation, test, or dependency exists |
| `:modules:integrations:integration-github` | Scaffold only | Build descriptor only; no GitHub implementation, test, or dependency exists |
| `:modules:integrations:integration-web` | Scaffold only | Build descriptor only; no web integration, test, or dependency exists |
| `:modules:security:security-auth` | Scaffold only | Build descriptor only; no authentication implementation, test, or dependency exists |
| `:modules:security:security-secrets` | Scaffold only | Build descriptor only; no implementation exists here; actual secret utilities are owned by `platform-config` |
| `:modules:security:security-audit` | Scaffold only | Build descriptor only; no implementation exists here; actual audit logging is owned by `platform-observability` |
| `:modules:infrastructure:infrastructure-persistence` | Scaffold only | Build descriptor only; no persistence implementation, entity, test, or dependency exists |
| `:modules:infrastructure:infrastructure-vector-store` | Scaffold only | Build descriptor only; no vector-store implementation, test, or dependency exists |
| `:modules:infrastructure:infrastructure-messaging` | Scaffold only | Build descriptor only; no messaging implementation, test, or dependency exists |
| `:modules:infrastructure:infrastructure-caching` | Scaffold only | Build descriptor only; no caching implementation, test, or dependency exists |
| `:modules:infrastructure:infrastructure-monitoring` | Scaffold only | Build descriptor only; no monitoring implementation, test, or dependency exists |

## Capability conflicts and unknowns

| Evidence conflict or unknown | Decision consequence |
| --- | --- |
| Four application names imply server, desktop, CLI, and web products, but each contains only a generic Spring Boot launcher | The projects are partially operational launch surfaces, not four working KAOS applications |
| All nine AI module names imply AI capabilities, but none contains source or project dependencies | They remain scaffolds; even `AiProperties` in platform-config proves configuration shape, not AI integration |
| Security-secrets and security-audit are empty while secret utilities and audit logging exist in platform modules | Classification follows owning source paths, not the more attractive security project names |
| Persistence, vector-store, messaging, caching, and monitoring projects are empty while configuration and Docker evidence exists elsewhere | External service configuration is not a working infrastructure capability |
| Shared READMEs describe intended concerns but contain no source | Documentation alone does not promote a project beyond scaffold status |
| The historical build passed together, but file inspection did not execute it during this story | Historical build evidence supports reuse decisions, not current operational status |
| No current organization-repository application consumes any historical project | There is no operational-now project or extraction candidate |

## Extraction decision

No surface qualifies as an extraction candidate. The preserved baseline shows
no independent consumer, release lifecycle, ownership boundary, deployment
requirement, measured isolation need, or scale pressure. Configuration and
observability are proven foundations, but their next use should begin as the
smallest behavior needed by the single application. Extraction can be
reconsidered only after current implementation supplies one of those missing
signals.

## Reproducible validation

Validation compares this table with the Story #978 inventory and confirms:

- 48 unique build-surface identifiers in each record;
- no missing, extra, or duplicate surface;
- exactly one approved classification per surface;
- counts of 0 operational, 4 partial, 4 proven foundation, 40 scaffold,
  0 future hypothesis, and 0 extraction candidate;
- all relative documentation links resolve;
- only documentation changes are present.

## Limitations

- This is a present evidence classification, not a permanent architectural
  verdict. New implementation can change a classification.
- The historical build was not executed in this story, so launch success and
  runtime integration remain unverified here.
- Story #980, not this record, selects the minimal active project and dependency
  path for the next features.
- No historical source or build registration has been changed or deleted.

## Handoff

Story #980 is next. It will use these classifications to publish the required-now
project set, the minimal next application and AI dependency path, and the
historical registrations that may leave the active graph without deleting
history.
