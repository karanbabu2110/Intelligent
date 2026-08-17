# Reusable foundation asset inventory

## Purpose

This inventory classifies proven foundation evidence from the preserved KAOS
baseline without importing its foundation-first architecture. It supports Story
000.01.02 under roadmap #814.

- Inspected baseline: `b9bd26dee098ef338286080042ba51f614804079`
- Source evidence: [`karanbabu2110/KAOS` at the baseline revision](https://github.com/karanbabu2110/KAOS/tree/b9bd26dee098ef338286080042ba51f614804079)
- Inspection date: 2026-08-17
- Destination: `Knowledge-Autonomous-Operating-System/KAOS`

No historical file was copied while producing this inventory.

## Classification meanings

- **Reuse now:** repository-neutral and safe to adopt with a focused review.
- **Reuse when needed:** inspect and selectively adapt only after the listed
  trigger exists; the old file or subsystem is not a present prerequisite.
- **Repository history only:** preserve as evidence, but do not carry it into
  the new application unless a later feature produces new evidence.

A classification is a recommendation for later adoption work. It does not
authorize copying an asset during this documentation story.

## Reuse now

| Asset | Baseline path | Evidence | Adoption condition |
| --- | --- | --- | --- |
| Text and formatting rules | `.editorconfig`, `.gitattributes` | Standalone UTF-8, final-newline, indentation, text normalization, and platform script line-ending rules | Review against the two current Markdown files, then adopt without importing build structure |
| Ignore and local-secret hygiene | `.gitignore` | Excludes Java/Gradle outputs, editor state, logs, environment files, secret material, models, embeddings, vector stores, coverage, and Docker data | Retain only patterns applicable to the evolving repository; verify that examples remain explicitly allowed |
| Project license | `LICENSE` | Complete Apache License 2.0 text with no dependency on the old module graph | Confirm organization ownership expectations, then adopt as the repository license |

These are the only assets with no product, framework, module, service, or
deployment dependency.

## Reuse when needed

| Asset | Baseline path | Evidence | Trigger and required adaptation |
| --- | --- | --- | --- |
| Gradle bootstrap | `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `gradle/libs.versions.toml` | Complete wrapper for Gradle 9.1.0 with URL validation and a centralized catalog | Adopt when the minimal Java application begins; review the Gradle and Java versions and retain only dependencies actually consumed |
| Minimal Java and test conventions | `build-logic/src/main/kotlin/dev.kaos.java.gradle.kts`, `dev.kaos.java-application.gradle.kts`, `dev.kaos.java-library.gradle.kts`, `dev.kaos.testing.gradle.kts` | Java 21, UTF-8, JUnit Platform, reports, and coverage behavior are implemented | Extract only repeated configuration after a working build exists; do not import the complete convention-plugin framework for one project |
| Configuration safety patterns | `modules/platform/platform-config/src/main/java/.../secret/`, `.../validation/`, and corresponding tests | Real environment-secret, masking, profile, validation, diagnostics, and Spring configuration code exists | Reconsider when application configuration or secret handling is implemented; port the smallest required behavior and tests without adopting the module wholesale |
| Logging patterns | `modules/platform/platform-observability/src/main/`, `src/test/` | Real correlation, audit, structured logging, Logback profiles, diagnostics, and three tests exist | Reconsider when the minimal application needs logging; remove dependencies on platform-config, Actuator, Micrometer, and web APIs unless currently required |
| CI setup pattern | `.github/workflows/reusable-build.yml` | Uses checkout, Java 21, Gradle setup, caching, reports, artifacts, and job summaries; baseline nightly builds completed successfully | Create a new minimal workflow after the first Gradle build exists; replace old task names and multi-module artifact paths |
| Security reporting concepts | `SECURITY.md` | Responsible disclosure, private-channel limitations, and explicit non-implementation statements are documented | Rewrite for the organization repository before external contributors or executable code arrive; remove links and control claims not present in the new repository |
| Local persistence services | `docker/docker-compose.yml`, `docker/postgres/`, `docker/redis/` | PostgreSQL/pgvector and Redis definitions include loopback ports, health checks, volumes, and initialization | Reconsider only when persistence, RAG, caching, or integration tests require these services; start with the one service currently consumed |
| Cross-platform script patterns | `scripts/common/`, `scripts/validation/`, `docker/scripts/common/` | PowerShell and Bash pairs implement shared requirements, lifecycle helpers, diagnostics, and validation | Reuse a pattern only after a repeated manual workflow exists; rewrite paths and prerequisites for the current repository |
| Focused documentation validation | `scripts/validation/validate-documentation-framework.ps1`, `.agents/skills/kaos-documentation-auditor/scripts/` | Deterministic structure, baseline, inventory, and link validation are implemented | Introduce only the checks needed when the small documentation set becomes difficult to validate manually; do not recreate the former hierarchy first |

Successful historical CI proves that these assets operated together at the
baseline. It does not prove that the whole system should be transplanted. A
representative successful run is the [2026-08-16 Nightly Foundation Build](https://github.com/karanbabu2110/KAOS/actions/runs/31922252428).

## Repository history only

| Asset | Baseline path | Evidence | Reason not to carry forward |
| --- | --- | --- | --- |
| Full project graph | `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties` | Registers four applications, shared libraries, platform modules, AI modules, automation, integrations, and security modules with global governance tasks | Encodes the foundation-first target architecture and global performance assumptions before current consumers exist |
| Remaining convention-plugin platform | Other files under `build-logic/` | Formatting, static analysis, dependency governance, packaging, reporting, Spring, and quality-gate plugins form an integrated framework | Too much build machinery for one small application; recover individual ideas only after repetition proves a need |
| Application launchers and capability modules | `applications/`, most of `modules/` | Four thin Spring Boot launchers exist; many named capability projects have only build descriptors or scaffolding | Names and registration are not working KAOS capabilities and would recreate the old conceptual overhead |
| Empty shared and test surfaces | `shared/`, `tests/` except separately identified candidates | Shared projects mostly contain README and build files; `tests/` contains thirteen README files and no test source | Organizational placeholders are not reusable behavior |
| Broad application configuration | `config/application*.yml`, `config/ai/`, `config/integrations/`, `config/observability/` | Defaults assume server, database, AI, security, storage, Actuator, and multiple environments | Introduces requirements before the corresponding capabilities exist |
| Old workflow suite | `.github/workflows/ci.yml`, `pull-request.yml`, `nightly-build.yml`, `foundation-readiness.yml`, `governance-guard.yml`, `dependency-validation.yml`, `developer-tooling-portability.yml` | Workflows invoke old quality gates, integration tests, artifacts, foundation certification, and governance scripts | Exact workflows depend on the old graph and would fail or impose obsolete gates in the new repository |
| Full developer lifecycle tooling | Most of `scripts/` and `tools/` | Setup, environment certification, databases, Docker lifecycle, generators, diagnostics, and governance expect the former repository layout | No current repeated workflow or consumer justifies the suite |
| Container certification framework | `docker/certification/`, `docker/docs/`, most Docker lifecycle scripts | Extensive certification, operations, diagnostics, and quality-gate documentation surrounds PostgreSQL and Redis | Operational framework is disproportionate before a current service is selected |
| Enterprise documentation hierarchy | `docs/`, `config/quality/documentation-baseline.json` | 309 tracked documentation files, category governance, templates, audits, and a strict inventory baseline exist | Recreating the hierarchy would restore the cognitive overhead the evolutionary roadmap is intended to remove |

## Explicit exclusions

- Capability names are not evidence of implemented AI, memory, RAG, agents,
  browser automation, desktop automation, or developer-assistant behavior.
- A historical passing workflow is not a current quality gate.
- Existing Spring modules do not require the new application to use Spring.
- Existing module boundaries do not determine the new package structure.
- Docker, PostgreSQL, Redis, Actuator, Micrometer, and web APIs remain optional
  until a current capability needs them.

## Adoption rule

When an asset trigger occurs:

1. Inspect the immutable baseline path again.
2. State the current consumer and smallest required behavior.
3. Copy or rewrite only that behavior and its focused verification.
4. Remove dependencies on unselected historical infrastructure.
5. Document the result in the active #814 descendant.

## Handoff

The [foundation asset and assumption decisions](foundation-assumption-decisions.md)
turn this inventory into a clear separation between retained engineering
assets, retired foundation-first assumptions, and protected constraints. Story
000.01.04 publishes the resulting
[Feature 000.01 reuse checkpoint](foundation-reuse-checkpoint.md).
