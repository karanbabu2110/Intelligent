# KAOS

KAOS is being rebuilt as one evolving Java application, delivering one useful
goal at a time. This organization repository is intentionally minimal: existing
foundation work is being verified before any source or build structure is
selected for reuse.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Active feature: [Feature 000.01 — Preserve the Existing Foundation Baseline](https://github.com/karanbabu2110/KAOS/issues/816)
- Completed stories:
  - [Story 000.01.01 — Record the Foundation Baseline](https://github.com/karanbabu2110/KAOS/issues/835)
  - [Story 000.01.02 — Inventory Reusable Foundation Assets](https://github.com/karanbabu2110/KAOS/issues/836)
  - [Story 000.01.03 — Separate Proven Assets from Foundation-First Assumptions](https://github.com/karanbabu2110/KAOS/issues/837)
  - [Story 000.01.04 — Publish the Foundation Reuse Checkpoint](https://github.com/karanbabu2110/KAOS/issues/838)
- Next feature after review and merge: [Feature 000.02 — Reclassify Existing Modules and Capabilities](https://github.com/karanbabu2110/KAOS/issues/817)
- Repository state: baseline documentation only; no application source has been adopted
- Verified evidence:
  - [Foundation baseline](docs/evolution/foundation-baseline.md)
  - [Reusable foundation asset inventory](docs/evolution/reusable-foundation-assets.md)
  - [Foundation asset and assumption decisions](docs/evolution/foundation-assumption-decisions.md)
  - [Foundation reuse checkpoint](docs/evolution/foundation-reuse-checkpoint.md)

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

## Next checkpoint

Feature 000.01 evidence is consolidated in the foundation reuse checkpoint.
Review and merge the feature-level pull request before activating Feature
000.02 and its first story, #978.
