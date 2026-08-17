# Simplified application validation

## Outcome

Task [#1047](https://github.com/karanbabu2110/KAOS/issues/1047) verifies that
the current KAOS baseline is a reproducible, safely terminating Java
application. It is deliberately small enough for one developer and an AI agent
to understand before product capabilities are added.

## Active graph

```text
Gradle root project kaos
└── Java application io.kaos.app.KaosApplication
    └── test io.kaos.app.KaosApplicationTest
```

- Java toolchain: 21.
- Production source set: one package, one entry point, and `package-info.java`.
- Test source set: one JUnit Jupiter test class with two focused assertions.
- Runtime dependencies: none.
- Test dependencies: the JUnit 5 BOM and Jupiter test API/engine only.
- Gradle subprojects, included builds, repositories, services, plugins, and
  external processes: none.

The canonical commands are:

```powershell
./gradlew.bat test --tests io.kaos.app.KaosApplicationTest --no-daemon
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --no-daemon
./gradlew.bat projects dependencies --no-daemon
```

## Startup and safe exit

`KaosApplication.main` prints exactly:

```text
KAOS application baseline is running.
```

It then returns normally. The focused test now invokes the selected `main`
method, captures its standard output, asserts the exact diagnostic, and can
finish only after the entry point returns. The canonical `run` command provides
the external Gradle-level proof.

The message says **baseline**, not AI system or product, so the diagnostic does
not claim behavior that has not been implemented.

## Configuration, diagnostics, and failure assessment

| Surface | Current evidence | Decision |
| --- | --- | --- |
| Configuration | The application reads no arguments, files, properties, environment variables, credentials, or secrets | No runtime configuration is required for this baseline |
| Logging | There is no logging dependency or framework | Standard output is sufficient for the single deterministic startup diagnostic |
| Diagnostics | One stable startup message identifies the selected application and baseline state | Assert the exact message in the focused test |
| Runtime failure | There is no input, persistence, network, external process, mutable state, or background work | No product failure path exists yet; exceptions would propagate and make Gradle `run` fail rather than being hidden |
| Build failure | Compilation and test failures make Gradle return a non-zero result | Keep clean build/test/check as the reproducible gate |
| Shutdown | No thread, resource, service, hook, or connection survives `main` | Normal method return is the safe-stop contract |

Configuration, structured logging, richer diagnostics, and error handling must
be added with the capability that creates their real requirements. Adding them
now would test scaffolding rather than current behavior.

## Quality and safety classification

This is a **Routine** increment under the
[proportional guardrails](proportional-quality-and-safety.md): it adds a
deterministic test and records existing local behavior. It does not access
credentials, private data, persistence, the network, the desktop, a browser, or
an external system. The safe rollback is the task commit.

## Expected validation results

| Command | Expected result |
| --- | --- |
| Focused test | Two startup assertions pass |
| Clean build/check | All Gradle lifecycle tasks pass |
| Run | Exact baseline diagnostic, normal exit, successful Gradle result |
| Projects | Root project only |
| Dependencies | No production compile or runtime dependency |

Actual results are recorded by Task #1048 in the final Epic 000 exit
checkpoint.

## Non-goals and limitations

- No AI provider, model, prompt, chat, RAG, memory, agent, tool, or automation
  behavior exists yet.
- No runtime configuration, structured logging, interactive input, persistence,
  network access, long-running lifecycle, or recoverable product failure exists.
- This task does not claim production, security, performance, scale, or
  enterprise readiness.
- It introduces no framework, module, service, repository, or runtime
  dependency.

## Next checkpoint

Task [#1048](https://github.com/karanbabu2110/KAOS/issues/1048) will run and
record the actual results, map Feature #822 acceptance criteria, decide whether
the foundation gate can close, and hand off to Epic #2 / Feature #839.
