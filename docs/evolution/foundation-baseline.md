# Foundation baseline

## Purpose

This record identifies the exact foundation snapshot available for inspection
while KAOS begins again in the organization repository. It preserves evidence;
it does not approve the previous architecture, modules, or source for reuse.

Recorded on 2026-08-17 for Story 000.01.01 under roadmap #814.

## Repository identities

| Role | Repository | Verified state |
| --- | --- | --- |
| Current destination | `Knowledge-Autonomous-Operating-System/KAOS` | Empty repository; unborn `main`; no application source adopted |
| Preserved source evidence | `karanbabu2110/KAOS` | Remote `main` resolves to the baseline revision below |
| Local recovery copy | `KAOS-personal-checkout-backup-20260817-0715` | Clean `main` worktree at the baseline revision when recorded |

The local recovery-copy name is operational evidence only. Development must not
depend on that machine-specific path; the immutable remote commit is the
portable evidence reference.

## Immutable baseline

- Branch at capture: `main`
- Revision: `b9bd26dee098ef338286080042ba51f614804079`
- Commit timestamp: `2026-08-05T21:49:40+05:30`
- Commit author: `KARAN BABU B`
- Commit subject: `Merge pull request #722 from karanbabu2110/feature/721-kaos-documentation-auditor`
- Remote evidence: [view the baseline commit](https://github.com/karanbabu2110/KAOS/commit/b9bd26dee098ef338286080042ba51f614804079)

The preserved checkout was clean, and its configured personal-repository remote
`main` resolved to the same full revision when this record was created.

## Observed repository surface

The baseline contains Gradle entry points and multiple application, module,
platform, shared, tooling, automation, test, and documentation paths. Important
top-level evidence includes:

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, and the Gradle wrapper
- `applications/`, `modules/`, `platform/`, and `shared/`
- `build-logic/`, `config/`, `scripts/`, and `tools/`
- `tests/`, `docs/`, `.github/`, and `.agents/`
- Docker, automation, examples, reports, resources, and security documentation

This list records only that the paths exist at the baseline revision. It does
not claim that they are operational, required, reusable, or appropriate for the
new application. Those decisions belong to later stories in Feature 000.01.

## Verification performed

The following read-only checks established the baseline:

```powershell
git -C <preserved-checkout> status --porcelain
git -C <preserved-checkout> branch --show-current
git -C <preserved-checkout> rev-parse HEAD
git -C <preserved-checkout> remote get-url upstream
git -C <preserved-checkout> ls-remote upstream refs/heads/main
git -C <preserved-checkout> show -s --format='%H%n%aI%n%an%n%s' HEAD
git -C <preserved-checkout> ls-tree --name-only HEAD
git -C <organization-checkout> remote get-url origin
git -C <organization-checkout> status --short --branch
```

Observed results:

- Preserved branch: `main`
- Preserved working tree: clean
- Local baseline revision: `b9bd26dee098ef338286080042ba51f614804079`
- Remote personal-repository `main`: the same revision
- Destination origin: `https://github.com/Knowledge-Autonomous-Operating-System/KAOS.git`
- Destination state before this documentation: no commits and no tracked source

## Boundaries and limitations

- No source, build configuration, module, or capability was migrated.
- No baseline path has been approved for reuse.
- Passing historical checks are not evidence that a capability works now.
- Previous issues outside #814 are not requirements or dependencies.
- GitHub issues currently remain in `karanbabu2110/KAOS`; transferring or
  recreating issue tracking in the organization repository is a separate decision.
- This checkpoint establishes `main` without adopting historical application source.

## Handoff

The [reusable foundation asset inventory](reusable-foundation-assets.md) records
the Story 000.01.02 classifications from this immutable snapshot. The
[foundation asset and assumption decisions](foundation-assumption-decisions.md)
record which assets remain available, which foundation-first assumptions are
retired, and which constraints stay protected. The
[foundation reuse checkpoint](foundation-reuse-checkpoint.md) is the concise
Feature 000.01 handoff.
