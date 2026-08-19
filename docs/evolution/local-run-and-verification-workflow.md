# Local run and verification workflow

## Outcome

Feature [#843](https://github.com/karanbabu2110/KAOS/issues/843) adds one
portable Gradle workflow that compiles, tests, packages, and smoke-runs the
current KAOS application from the repository root. It uses the existing Gradle
wrapper and does not add an operating-system-specific script, build plugin,
runtime dependency, service, container, or deployment system.

## Canonical command

PowerShell or Command Prompt:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

POSIX shell:

```bash
./gradlew clean verifyLocal --no-daemon --warning-mode=all
```

`clean` is explicit rather than hidden inside `verifyLocal`. A developer can
choose fast incremental verification with `verifyLocal` or a from-clean-state
checkpoint with the canonical command above.

## Task contract and order

| Task | Responsibility |
| --- | --- |
| `build` | Compile production/test source, run all tests/checks, create the JAR, start scripts, ZIP, and TAR distributions |
| `localStatus` | Start the real application entry point with `status` and safe deterministic configuration |
| `localHelp` | Start the real application entry point with `help` and safe deterministic configuration |
| `verifyLocal` | Require the build and smoke tasks, then print the completion checkpoint |

The enforced execution order is:

```text
clean (only when explicitly requested)
  -> compile/package/test/check/build
  -> localStatus
  -> localHelp
  -> verifyLocal completion checkpoint
```

`localStatus` must run after `build`, and `localHelp` must run after
`localStatus`. Gradle dependencies ensure the completion action runs only after
every prerequisite succeeds.

Successful smoke output includes:

```text
KAOS application baseline is running.
Usage: kaos [status|help]
...
KAOS local verification passed: build, tests, package, status, and help.
```

The exact help text remains owned and tested by the application command
contract rather than duplicated in the Gradle build.

## Deterministic configuration

Both smoke tasks launch the same `io.kaos.app.KaosApplication` main class used
by `run`. They pass JVM system property `kaos.app.name=KAOS`. The system
property has precedence over `KAOS_APP_NAME`, so a developer's local
application-name environment value cannot change or break the verification
output.

This was verified with an intentionally invalid inherited
`KAOS_APP_NAME=inherited-invalid-value!`; both smoke tasks still succeeded with
the exact `KAOS` output. Ordinary `run` remains user-controlled and continues
to apply the configuration precedence documented by Feature #840.

## Inputs, outputs, lifecycle, and ownership

- **Inputs:** repository source/build files, the Gradle wrapper, the configured
  Java 21 toolchain, dependency cache/network when artifacts are not already
  available, and explicit Gradle flags.
- **Outputs:** compiled classes, test reports, JAR/distributions under `build/`,
  application smoke output, Gradle diagnostics, and a final success line.
- **State:** only ordinary reproducible Gradle output under `build/` and normal
  Gradle caches outside the repository; KAOS product state is not created.
- **Lifecycle:** optional explicit clean, build/test/package, two sequential
  local Java executions, completion message, and process exit.
- **Ownership:** the root Gradle application build owns this workflow because
  KAOS still has one application and no demonstrated module/service boundary.

## Failure, cancellation, and recovery

Any compilation, test, check, packaging, Java startup, `status`, or `help`
failure causes its Gradle task to fail and prevents the final success message.
Gradle returns a nonzero result with the failing task and its diagnostics.

Cancellation uses the shell's normal interrupt mechanism, such as Ctrl+C.
There is no retry, rollback, background worker, or application resource to
clean up. A partial `build/` directory contains only reproducible build output.
Recovery is correcting the reported source, configuration, toolchain, cache,
or dependency problem and rerunning the command; include explicit `clean` when
a from-clean-state retry is desired.

The workflow does not swallow failures or convert them into a successful
checkpoint. It also does not delete anything outside Gradle's normal `clean`
target, which is invoked only when the developer writes it in the command.

## Security and privacy

The smoke commands use a constant non-secret application name and the current
read-only `status`/`help` behavior. They do not read credentials, accept user
content, access product files, contact a network service, persist application
state, perform a privileged/destructive action, or send diagnostics remotely.

Gradle may download declared build/test dependencies when the local cache is
empty. That is build-tool behavior, not KAOS runtime behavior. The current
application still has no runtime dependency.

## Validation evidence

On 2026-08-19:

| Check | Result |
| --- | --- |
| Verification task catalog | `localStatus`, `localHelp`, and `verifyLocal` visible under Verification |
| Isolated smoke run | Status/help passed with an invalid inherited `KAOS_APP_NAME` |
| Clean aggregate run | 11 actionable tasks executed; build, 28 tests, packages, status, help, and checkpoint passed |
| Incremental aggregate run | Build work was up-to-date; both smoke tasks and the completion checkpoint still executed |
| Dry-run order | Build graph precedes `localStatus`, `localHelp`, and `verifyLocal` in that order |
| Ordinary application run | `run --args=status` remains successful |
| Build warnings | No warning-mode-all failure or warning introduced |
| Development version | Remains `0.0.2-SNAPSHOT`; no tag or release created |

Canonical verification:

```powershell
./gradlew.bat tasks --group verification --no-daemon
./gradlew.bat clean verifyLocal --dry-run --no-daemon
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
./gradlew.bat run --args=status --no-daemon
```

## Feature 001.06 acceptance map

| Criterion | Evidence |
| --- | --- |
| One explicit outcome | One wrapper command builds, tests, packages, and smoke-runs KAOS |
| Outcome works | Clean aggregate and separate smoke commands pass with exact output |
| Failure and safety proportional | Gradle fail-fast semantics, explicit clean, no product/external state, normal interruption |
| Repeatable validation | Task catalog, dry-run graph, isolated smoke run, repeated clean aggregate runs |
| Documentation matches behavior | This record provides commands, order, inputs, outputs, lifecycle, recovery, safety, and limits |
| No future architecture prerequisite | Three root-build tasks; no script/plugin/module/service/repository added |
| Security, privacy, ownership, and control | Constant local input, no secrets or external runtime action, explicit destructive clean only |
| Current roadmap evidence is sufficient | Feature #843, Tasks #1059/#1060, build file, test suite, README, and checkpoints |

## Limitations and handoff

The workflow is not CI/CD, deployment, installation, a release pipeline, a
cross-platform test matrix, production monitoring, performance/load testing,
or external-service verification. POSIX wrapper syntax is documented because
the Gradle wrapper and tasks are portable, but this feature was executed only
on the recorded Windows x64/OpenJDK 21 host.

It validates the current minimal application only. AI connectivity and its
external dependency/failure behavior begin in Epic #3 / Feature #845 after
Epic 001 is closed and current evidence still supports that next step.
