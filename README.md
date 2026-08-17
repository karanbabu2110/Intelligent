# KAOS

KAOS is being rebuilt as one evolving Java application, delivering one useful
goal at a time. This organization repository is intentionally minimal: existing
foundation work is being verified before any source or build structure is
selected for reuse.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Active feature: [Feature 000.01 — Preserve the Existing Foundation Baseline](https://github.com/karanbabu2110/KAOS/issues/816)
- Completed story: [Story 000.01.01 — Record the Foundation Baseline](https://github.com/karanbabu2110/KAOS/issues/835)
- Next story: [Story 000.01.02 — Inventory Reusable Foundation Assets](https://github.com/karanbabu2110/KAOS/issues/836)
- Repository state: baseline documentation only; no application source has been adopted
- Verified evidence: [Foundation baseline](docs/evolution/foundation-baseline.md)

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

## Next checkpoint

After the baseline record is accepted, Story 000.01.02 will inventory reusable
foundation assets. No historical source should be copied into this repository
before that evidence-based classification.
