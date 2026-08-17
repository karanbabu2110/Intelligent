# Active capability classification and dependency path

## Resume here

This is the authoritative Feature 000.02 checkpoint. It converts the
[registered project inventory](registered-project-capability-inventory.md) and
[evidence-backed classifications](active-project-classification.md) into an
actionable starting graph for the single evolving KAOS application.

- Roadmap: [#814](https://github.com/karanbabu2110/KAOS/issues/814)
- Epic: [#815](https://github.com/karanbabu2110/KAOS/issues/815)
- Feature: [#817](https://github.com/karanbabu2110/KAOS/issues/817)
- Final story: [#980](https://github.com/karanbabu2110/KAOS/issues/980)
- Next feature after merge: [#818](https://github.com/karanbabu2110/KAOS/issues/818)
- First next story: [#981](https://github.com/karanbabu2110/KAOS/issues/981)

## Required-now project set

No historical subproject or included build is required by the current
organization repository. It contains documentation only and has no application
consumer. Selecting a historical project now would turn foundation evidence
back into an implementation prerequisite.

The target active Gradle graph for Feature #818 is exactly one root project:

```text
KAOS root project (:)
└── no included subprojects
```

The root identity is retained; the historical root build configuration is not
approved wholesale. Feature #818 may create the smallest root settings, build,
wrapper, and verification path required for current development. It must not
copy global lifecycle, reporting, governance, convention-plugin, or
multi-project machinery unless its new build demonstrates a current need.

## Minimal dependency paths

### First Java application

```text
root project
└── src/main/java application code
```

- Internal project dependencies: **none**.
- Initial code belongs in the root source set and may use packages as behavior
  grows.
- Feature #819 decides the smallest runnable application behavior and concrete
  Java/Spring choice; this checkpoint does not preselect an application
  framework.
- A Gradle subproject is introduced only after a demonstrated build boundary,
  not to recreate an historical application name.

### First AI integration

```text
root application
└── one provider/client dependency selected by the consuming AI feature
```

- Internal project dependencies: **none**.
- Historical `ai-*` projects are empty scaffolds and are not a dependency path.
- The AI provider, SDK, protocol, and version remain decisions for Epic 002,
  where a concrete AI request is the consumer.
- Configuration and secret handling begin as the smallest tested behavior in
  the root application. Proven platform-config techniques may be selectively
  ported, but the module is not required.
- Logging begins with the smallest diagnostics required by the application.
  Proven platform-observability techniques may be selectively ported, but the
  module is not required.

## Historical build-surface dispositions

Every one of the 48 classified historical build surfaces has a disposition.

| Surface group | Count | Active-graph disposition | Reconsideration trigger |
| --- | ---: | --- | --- |
| Root project `:` | 1 | Retain the root role; replace the historical foundation-first configuration with the minimal current build in Feature #818 | Add only behavior consumed by current build or release commands |
| Four application subprojects | 4 | Inactive; do not register or copy the generic launchers | A current feature demonstrates a separate build boundary rather than merely a runtime entry point |
| Ten shared projects | 10 | Inactive; README/build-only placeholders are not dependencies | Repeated implemented code demonstrates an independently useful shared boundary |
| Three scaffold-only platform projects | 3 | Inactive | Current implementation needs a build boundary for common, core, or event behavior |
| Platform config and observability | 2 | Inactive but available as proven reusable foundation evidence | A current application needs the specific configuration, secret, logging, or diagnostic behavior; port the smallest part first |
| Nine AI projects | 9 | Inactive; all are scaffolds | A working AI capability grows enough to demonstrate a build boundary |
| Four automation projects | 4 | Inactive; all are scaffolds | Implemented automation behavior demonstrates a distinct build or lifecycle boundary |
| Six integration projects | 6 | Inactive; all are scaffolds | A working external integration demonstrates independent reuse or lifecycle |
| Three security projects | 3 | Inactive; all are scaffolds | Current security behavior requires a distinct verified boundary; security outcomes themselves remain mandatory where risk appears |
| Five infrastructure projects | 5 | Inactive; all are scaffolds | A consumed persistence, vector, messaging, cache, or monitoring implementation demonstrates a build boundary |
| Included `build-logic` build | 1 | Inactive; do not include the convention-plugin platform in the one-project build | Repeated build configuration creates measurable duplication or a separately testable build-logic need |
| **Total** | **48** | **One active root role; 47 inactive historical surfaces** | **Evidence, not roadmap naming, controls reconsideration** |

Inactive means absent from the new active graph. It does not mean deleted,
rejected permanently, or unavailable for inspection.

## History preservation and recovery

- The immutable evidence revision remains
  `b9bd26dee098ef338286080042ba51f614804079` in `karanbabu2110/KAOS`.
- The inventory and classification records link the exact registrations,
  owning paths, source counts, dependencies, and decisions.
- Feature #818 changes only the organization repository. It does not rewrite or
  delete the preserved repository, branches, source, or Git history.
- When a trigger occurs, inspect the immutable path again, identify the current
  consumer, port only the required behavior and focused tests, and document the
  new evidence in the active #814 descendant.

## Feature 000.02 acceptance map

| Feature criterion | Evidence and result |
| --- | --- |
| Every registered project and identified capability has one classification | `active-project-classification.md` contains 48 unique rows: root, 46 subprojects, and included build |
| Every classification cites current evidence | Each row cites source, tests, build descriptor, README, dependency, or explicit absence recorded by the inventory |
| Planned names are not treated as implementation | Forty empty/README-only surfaces are classified scaffold only; application launchers are not described as channel capabilities |
| Active dependency path is explicit and minimal | This checkpoint selects one root project and zero internal dependencies for both the first application and AI integration |
| Validation is recorded | The validation section below records coverage, count, link, and Git-scope checks |
| Documentation matches behavior and limitations | All records distinguish a documentation-only destination from preserved historical implementation |
| Evidence uses #814 descendants and current artifacts | Planning links use #814, #815, #817, #980, #818, and #981; detailed evidence is repository-local |
| Next approved work is explicit | Merge Feature #817, then begin Feature #818 with Story #981 |

## Validation record

Focused validation confirms:

- 46 unique historical subproject registrations and 47 main-build projects
  including root;
- 48 unique classifications including the separate included build;
- disposition group counts total 48;
- the target graph has one active root and zero internal project dependencies;
- all relative Markdown links resolve;
- no source, build, module, repository, or service was added, removed, or
  modified;
- Feature #817 changes are documentation only.

## Limitations and decisions deferred

- Feature #818 must create or adopt the actual minimal Gradle files and prove
  the target graph; this feature only decides the graph.
- Feature #819 decides the runnable application implementation and framework.
- Epic 002 decides the first AI provider and external dependency.
- There is no current performance, ownership, release, isolation, deployment,
  or scale evidence for modules, repositories, or services.
- Proven historical configuration and observability behavior still requires
  focused adaptation and current tests when first consumed.

## Next action

Feature [#818](https://github.com/karanbabu2110/KAOS/issues/818) is active. Its
[minimal active Gradle graph contract](minimal-gradle-graph.md) records Story
[#981](https://github.com/karanbabu2110/KAOS/issues/981). Story #982 is next and
will implement that contract.
