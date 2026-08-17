# Foundation asset and assumption decisions

## Purpose

This record decides what the evolving KAOS application retains from the
preserved foundation and what no longer constrains present delivery. It is the
authoritative result of Story 000.01.03 under roadmap #814.

The decision inputs are:

- the immutable foundation baseline at
  `b9bd26dee098ef338286080042ba51f614804079`;
- the [foundation baseline](foundation-baseline.md) and
  [reusable asset inventory](reusable-foundation-assets.md);
- the current delivery rules in roadmap #814, Epic #815, Feature #816, and
  Story #837.

Legacy issues, scaffold names, and aspirational architecture are not decision
evidence. No historical source was copied and no application architecture was
introduced by this story.

## Decision rule

KAOS retains evidence and proven techniques, not the former system shape. An
asset may enter the active application only when a current consumer exists and
the smallest useful part can be adopted with focused verification.

The default progression remains:

1. implement behavior in the single application;
2. organize repeated behavior with packages;
3. add a Gradle module for a demonstrated build boundary;
4. extract a library or repository for independent reuse or lifecycle;
5. deploy a separate service for measured operational need.

Deferral is not rejection. A deferred asset can be reconsidered when its
documented trigger occurs, but it is not a prerequisite before then.

## Retained assets

| Retained asset | Current evidence | Present decision |
| --- | --- | --- |
| Immutable source and Git history | The baseline record identifies the preserved repository and exact revision | Keep as read-only evidence so useful behavior can be rediscovered without importing the former architecture |
| Repository-neutral text, ignore, and license assets | The inventory classifies `.editorconfig`, `.gitattributes`, `.gitignore`, and `LICENSE` as **Reuse now** | Keep available for focused adoption after checking each file against the current repository |
| Minimal Java and Gradle techniques | The inventory identifies a working wrapper plus Java 21, UTF-8, JUnit, report, and coverage conventions | Reuse only the minimum needed when the first runnable Java application is created; the old build platform is not retained |
| Configuration and secret-handling techniques | Historical implementation and tests exist at the inventory paths | Reconsider when the application first accepts configuration or secrets; retain masking, validation, and safe failure behavior without importing the old module |
| Focused logging and diagnostics techniques | Historical correlation, audit, structured logging, and tests exist at the inventory paths | Reconsider when a current capability needs observability; adopt only behavior whose dependencies are already justified |
| Focused testing and validation | Roadmap #814 requires proportional testing and the inventory identifies reusable test and validation ideas | Test each current capability at its risk boundary; do not restore global gates before their consumers exist |
| Minimal CI pattern | A historically successful reusable build workflow is recorded in the inventory | Create a new workflow after a real build command exists, using only current tasks and artifacts |
| Security disclosure concepts | `SECURITY.md` contains usable disclosure concepts but also historical claims | Rewrite for the current repository when executable code or external contribution creates a real reporting surface |
| Optional local persistence and tooling patterns | PostgreSQL/pgvector, Redis, scripts, and container evidence have explicit triggers in the inventory | Reconsider one asset at a time only when persistence, RAG, caching, integration testing, or a repeated workflow requires it |
| Concise evolutionary documentation | The current repository contains three small, linked evidence records | Continue documenting current behavior, decisions, limitations, and the next checkpoint without recreating the former documentation hierarchy |

Retention means the asset remains available for evidence-backed reuse. It does
not mean the original file, dependency graph, or owning module is approved.

## Retired foundation-first assumptions

| Retired assumption | Current evidence | Decision |
| --- | --- | --- |
| The complete platform foundation must exist before product capabilities | Roadmap #814 requires the smallest end-to-end implementation that proves value | A capability begins with only the foundation it currently consumes |
| Every anticipated capability needs a predefined module | The inventory records many named modules with build descriptors or scaffolding but no proven capability behavior | Start inside the application and introduce a module only for an observed build boundary |
| The old Gradle project graph is the target architecture | `settings.gradle.kts` registered applications and subsystems before current consumers existed | Build the smallest runnable project first; recover isolated build techniques only as needed |
| Cross-cutting platforms must be generalized before first use | The old build logic, configuration, observability, and governance assets form integrated frameworks | Implement the first concrete use locally and generalize only after repetition or a real boundary appears |
| Enterprise CI and quality gates are prerequisites for initial experiments | Historical workflows depend on old task names, modules, artifacts, and certification scripts | Add focused automation after a stable current command exists; increase gates with demonstrated risk |
| Databases, caches, containers, and environment profiles must be provisioned upfront | Historical configuration assumes PostgreSQL, Redis, AI, security, storage, Actuator, and several environments | Introduce one external dependency only when the active feature needs it |
| Documentation must describe the complete future platform before implementation | The baseline contains 309 tracked documentation files and a strict hierarchy | Document verified behavior and near-term decisions; add structure when navigation or ownership becomes a current problem |
| Plugin systems, distributed communication, and service boundaries are default extensibility mechanisms | Epic #815 excludes frameworks and service boundaries created only for anticipated use | Prefer direct in-process collaboration; extract only in response to measured reuse, lifecycle, isolation, deployment, or scale needs |
| Repository or service separation is implied by capability separation | Roadmap #814 distinguishes packages, modules, repositories, and services by increasingly strong evidence thresholds | Develop capabilities independently in intent and tests without forcing independent deployment or repositories |
| Previous issues and passing historical builds remain active requirements | Roadmap #814 declares only its descendants executable, and the baseline warns that historical checks do not prove current behavior | Treat old issues and builds as discoverable history only; restate any useful requirement under the active roadmap |

These assumptions are retired as delivery constraints. The corresponding
techniques may still be chosen later when current evidence meets the decision
rule.

## Protected constraints

Evolutionary delivery does not permit deferring safeguards whose absence could
cause material or irreversible harm.

| Protected constraint | Risk that requires protection | Required treatment |
| --- | --- | --- |
| Credentials and secrets | Disclosure can compromise user accounts, infrastructure, and paid services | Never commit real secrets; use explicit external configuration, safe examples, masking, and focused secret-handling tests when secrets first appear |
| Privacy and personal data | Collection or transmission can expose sensitive user information | Keep behavior local-first, identify data leaving the process, minimize collection, and require explicit configuration for external operation |
| Persistent and user-controlled data | Schema or storage changes can corrupt or permanently lose state | Define ownership, failure behavior, backup or recovery expectations, and migration verification before making irreversible changes |
| Destructive and irreversible actions | Automation can delete files, records, accounts, or remote state | Resolve exact targets, require appropriate confirmation, prefer recoverable operations, and verify the result |
| External trust boundaries | Network calls, tools, browsers, and agents can cross authority boundaries or process untrusted input | Validate inputs and outputs, constrain permissions, expose failures, and test the relevant boundary when introduced |
| Compatibility with real consumers | Uncontrolled changes can break persisted data, public APIs, plugins, or independently released components | Add compatibility policy only when a real consumer exists, then version or migrate that boundary deliberately |
| Dependency and artifact integrity | Executable dependencies can introduce vulnerable or unrepeatable behavior | Pin and verify the dependencies actually used by the current build; broaden governance in proportion to dependency and release risk |
| Licensing and provenance | Unclear ownership or copied code can create legal and maintenance risk | Preserve source attribution and license evidence; review an asset before copying or rewriting it |
| Security-sensitive diagnostics | Logs and traces can leak secrets or personal data | Prefer useful but minimal diagnostics, redact sensitive values, and test masking when such data can enter logs |
| Source and decision history | Premature deletion can destroy evidence needed for recovery or future reuse | Preserve Git history and historical repositories unless deletion is separately justified and authorized |

These constraints define required outcomes, not mandatory frameworks. Their
implementation should remain the smallest mechanism proportional to the active
feature and its risk.

## Evidence gaps and limitations

- This repository still contains documentation only. No Java runtime, build,
  application capability, security control, persistence behavior, or CI check
  is currently implemented here.
- Historical implementation proves that some techniques existed together at
  the baseline; it does not prove that they fit a future capability unchanged.
- The organization repository and the active roadmap issues remain in separate
  GitHub repositories. Links are deliberate, but migration has not been
  evaluated.
- No measured runtime, scaling, isolation, ownership, or release evidence
  currently justifies multiple Gradle modules, repositories, or services.
- Each retained asset still requires a focused review when its trigger occurs.

## Validation record

The decision was produced by inspecting the current repository files and the
current bodies of issues #814, #815, #816, #837, and #838. Validation for this
story checks that:

- retained assets, retired assumptions, and protected constraints are separate;
- every table row cites repository evidence, a current roadmap rule, or an
  explicit risk;
- no unused framework or scaffold is a current prerequisite;
- internal documentation links resolve;
- only documentation changes are present.

## Handoff

Story 000.01.03 establishes the decisions. The
[foundation reuse checkpoint](foundation-reuse-checkpoint.md) consolidates the
full Feature 000.01 evidence. Feature #817 does not start until Feature #816 is
reviewed and merged.
