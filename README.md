# KAOS

KAOS is one evolving Java application, delivering one useful goal at a time.
The development-model reset is complete; capabilities now grow incrementally
inside the verified single application.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Completed epics: [Epic 000 — Development Model Reset](https://github.com/karanbabu2110/KAOS/issues/815) and [Epic 001 — Minimal KAOS Application](https://github.com/karanbabu2110/KAOS/issues/2)
- Active epic: [Epic 002 — First AI Integration](https://github.com/karanbabu2110/KAOS/issues/3)
- Active feature: [Feature 002.01 — Ollama Connectivity](https://github.com/karanbabu2110/KAOS/issues/845)
- Repository state: one root Gradle/Java 21 application with one production entry point, no runtime dependency, and twenty-eight focused tests including five real child-process/timeout scenarios
- Completed features, stories, tasks, and verified evidence: [completed work and evidence](docs/evolution/completed-work-and-evidence.md)

## Architecture

The [living KAOS architecture website](ui/architecture/index.html) shows the
verified current runtime, the next approved capability, future capability
candidates, and the evidence required before introducing modules or services.
It is a structured, buildless UI that can grow into multiple pages or an
application when real complexity justifies that evolution. See its
[local run and maintenance guide](ui/architecture/README.md).

Every feature pull request that changes packages, dependencies, integrations,
data ownership, or runtime boundaries must update the diagram. Implemented and
planned elements must remain visually distinct.

## Developer guide

Start with the [KAOS developer guide](docs/development/developer-guide.md) for
prerequisites, setup, application commands, focused and complete test commands,
build outputs, troubleshooting, and the feature delivery workflow.

## Version

The latest release is **0.0.1**, tagged as
[`v0.0.1`](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/releases/tag/v0.0.1).
Ongoing development uses Gradle version **0.0.2-SNAPSHOT** so unreleased work
cannot be confused with that baseline.

This is an initial-development baseline. It proves the runnable Java
application and development workflow; it is not a production-ready KAOS or AI
capability release. See the [0.0.1 release notes](docs/releases/v0.0.1.md).

## Run the application

```powershell
./gradlew.bat run --args=status
```

The no-argument form remains supported. Both forms print
`KAOS application baseline is running.` and exit successfully. Run
`./gradlew.bat run --args=help` for the exact supported syntax. Unknown
commands or extra arguments produce safe guidance and a nonzero result without
echoing the supplied values.

Handled startup or application failures return exit code `1` and emit one safe
record such as `ERROR [KAOS-CONFIG-001] ...` on standard error. Expected CLI
usage errors retain exit code `2`. Exception messages, stack traces, arguments,
and configured values are not logged.

The application name can be overridden locally. For the Gradle run workflow,
set the environment variable:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run
```

Direct JVM launches may instead set `-Dkaos.app.name="Local KAOS"`; that system
property takes precedence over `KAOS_APP_NAME`.

The default is `KAOS`. Names are trimmed, limited to 64 Unicode characters, and
may contain letters, numbers, spaces, periods, underscores, or hyphens. No
secret, provider, remote, or file configuration is implemented yet.

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

The living architecture page is part of the feature definition of done whenever
a feature changes the system structure or its verified architectural status.

## Verify the local application

Run the complete from-clean-state checkpoint:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

This compiles, runs all tests, packages the application, executes deterministic
`status` and `help` smoke commands, and prints a final success checkpoint only
when every prerequisite passes. Use `verifyLocal` without `clean` for an
incremental check.

## Next checkpoint

Epics 000-001 are complete. Project 1 now activates #814, Epic #3, and Feature
#845. Create Feature 002.01 stories or direct tasks just in
time after reviewing its current contract; no tag or release is created unless
the user explicitly requests one.
