# KAOS

KAOS is being rebuilt as one evolving Java application, delivering one useful
goal at a time. This organization repository is intentionally minimal: existing
foundation work is being verified before any source or build structure is
selected for reuse.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Completed feature: [Feature 000.01 — Preserve the Existing Foundation Baseline](https://github.com/karanbabu2110/KAOS/issues/816)
- Completed feature: [Feature 000.02 — Reclassify Existing Modules and Capabilities](https://github.com/karanbabu2110/KAOS/issues/817)
- Completed Feature 000.01 stories:
  - [Story 000.01.01 — Record the Foundation Baseline](https://github.com/karanbabu2110/KAOS/issues/835)
  - [Story 000.01.02 — Inventory Reusable Foundation Assets](https://github.com/karanbabu2110/KAOS/issues/836)
  - [Story 000.01.03 — Separate Proven Assets from Foundation-First Assumptions](https://github.com/karanbabu2110/KAOS/issues/837)
  - [Story 000.01.04 — Publish the Foundation Reuse Checkpoint](https://github.com/karanbabu2110/KAOS/issues/838)
- Completed Feature 000.02 stories:
  - [Story 000.02.01 — Inventory Registered Projects and Capability Surfaces](https://github.com/karanbabu2110/KAOS/issues/978)
  - [Story 000.02.02 — Classify Each Project and Capability from Current Evidence](https://github.com/karanbabu2110/KAOS/issues/979)
  - [Story 000.02.03 — Publish the Active Capability Classification and Dependency Path](https://github.com/karanbabu2110/KAOS/issues/980)
- Completed feature: [Feature 000.03 — Simplify the Active Gradle Build](https://github.com/karanbabu2110/KAOS/issues/818)
- Completed Feature 000.03 stories:
  - [Story 000.03.01 — Define the Minimal Active Gradle Project Graph](https://github.com/karanbabu2110/KAOS/issues/981)
  - [Story 000.03.02 — Apply the Active Gradle Build Simplification](https://github.com/karanbabu2110/KAOS/issues/982)
  - [Story 000.03.03 — Verify and Document the Focused Gradle Build](https://github.com/karanbabu2110/KAOS/issues/983)
- Active feature: [Feature 000.04 — Establish the Single KAOS Application](https://github.com/karanbabu2110/KAOS/issues/819)
- Completed Feature 000.04 stories:
  - [Story 000.04.01 — Select the Single KAOS Application Entry Point](https://github.com/karanbabu2110/KAOS/issues/984)
- Next story: [Story 000.04.02 — Establish the Package-First Application Structure](https://github.com/karanbabu2110/KAOS/issues/985)
- Repository state: one root Gradle/Java 21 application with one dependency-free entry point
- Verified evidence:
  - [Foundation baseline](docs/evolution/foundation-baseline.md)
  - [Reusable foundation asset inventory](docs/evolution/reusable-foundation-assets.md)
  - [Foundation asset and assumption decisions](docs/evolution/foundation-assumption-decisions.md)
  - [Foundation reuse checkpoint](docs/evolution/foundation-reuse-checkpoint.md)
  - [Registered project and capability surface inventory](docs/evolution/registered-project-capability-inventory.md)
  - [Active project and capability classification](docs/evolution/active-project-classification.md)
  - [Active capability classification and dependency path](docs/evolution/active-capability-dependency-path.md)
  - [Minimal active Gradle graph contract](docs/evolution/minimal-gradle-graph.md)
  - [Focused Gradle build workflow](docs/evolution/focused-gradle-build.md)
  - [Single KAOS application entry point](docs/evolution/single-application-entry-point.md)

## Run the application

```powershell
./gradlew.bat run
```

The current application prints `KAOS application baseline is running.` and
exits. This verifies the bootstrap only; no KAOS product capability is claimed
yet.

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

## Next checkpoint

Features 000.01 through 000.03 are complete. Feature 000.04 is active, and
Story #984 establishes the one runnable application entry point. Story #985 is
next and will add the smallest package-first internal structure without adding
a module, repository, service, or speculative framework.
