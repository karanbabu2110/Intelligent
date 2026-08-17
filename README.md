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
- Active feature: [Feature 000.03 — Simplify the Active Gradle Build](https://github.com/karanbabu2110/KAOS/issues/818)
- Completed Feature 000.03 stories:
  - [Story 000.03.01 — Define the Minimal Active Gradle Project Graph](https://github.com/karanbabu2110/KAOS/issues/981)
  - [Story 000.03.02 — Apply the Active Gradle Build Simplification](https://github.com/karanbabu2110/KAOS/issues/982)
  - [Story 000.03.03 — Verify and Document the Focused Gradle Build](https://github.com/karanbabu2110/KAOS/issues/983)
- Next feature after merge: [Feature 000.04 — Establish the Single KAOS Application](https://github.com/karanbabu2110/KAOS/issues/819)
- Repository state: one root Gradle/Java 21 build; no application source or external dependency has been adopted
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

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

## Next checkpoint

Feature 000.02 is complete. Feature 000.03 now has a verified one-root Gradle
build and canonical developer workflow. Merge Feature 000.03 before Feature
#819 begins with Story #984.
