# Minimal error handling and logging

## Outcome

Feature [#841](https://github.com/karanbabu2110/KAOS/issues/841) adds a
single application failure boundary around configuration loading and command
execution. Expected results remain distinct from handled application failures,
and an operator receives a stable diagnostic code plus safe recovery guidance
instead of a raw Java stack trace.

The implementation remains in `KaosApplication` and uses only `PrintStream`
and JDK types. It does not introduce a logging framework, configuration file,
log file, remote sink, module, service, retry worker, or generalized exception
hierarchy.

## Result and stream policy

| Result class | Exit code | Stream | Format |
| --- | ---: | --- | --- |
| Successful command | 0 | standard output | command result |
| Expected CLI usage error | 2 | standard error | actionable usage guidance |
| Handled startup/application failure | 1 | standard error | `ERROR [code] safe message` |

Usage errors are expected caller-correctable results, so they are not labeled
as internal application errors. Handled startup/application failures receive a
stable code. No successful command emits an additional log record.

## Diagnostic code catalog

| Code | Meaning | Recovery |
| --- | --- | --- |
| `KAOS-CONFIG-001` | A configured value is invalid | Correct or remove `kaos.app.name`/`KAOS_APP_NAME`, then restart |
| `KAOS-CONFIG-002` | Local process configuration cannot be read | Check process permissions, then restart |
| `KAOS-APP-001` | An unexpected runtime failure occurred during startup or command execution | Restart and retry; if repeatable, report the code and reproduction command |

The configuration-loading phase is separate from command execution. This
prevents a future command-side `IllegalArgumentException` or
`IllegalStateException` from being mislabeled as a configuration failure.
JVM `Error` conditions are not caught or converted into ordinary application
results.

## Examples

Successful status:

```powershell
./gradlew.bat run --args=status --no-daemon
```

Output and application result:

```text
KAOS application baseline is running.
exit 0
```

Expected usage failure:

```powershell
./gradlew.bat classes --no-daemon
& java -cp build/classes/java/main io.kaos.app.KaosApplication unknown
$LASTEXITCODE
```

Standard error and result:

```text
Unknown command. Run 'kaos help' for usage.
2
```

Invalid configuration:

```powershell
$env:KAOS_APP_NAME = "invalid!"
& java -cp build/classes/java/main io.kaos.app.KaosApplication status
$LASTEXITCODE
Remove-Item Env:KAOS_APP_NAME
```

Standard error and result:

```text
ERROR [KAOS-CONFIG-001] Invalid application configuration. Check kaos.app.name or KAOS_APP_NAME and restart.
1
```

Gradle reports a failed `run` task when the application exits nonzero. Direct
Java invocation is used above when the application's exact exit code matters.

## Inputs, outputs, state, lifecycle, and ownership

- **Inputs:** the existing local configuration and CLI arguments; exception
  content is never treated as diagnostic output.
- **Outputs:** command results on standard output, expected usage guidance on
  standard error, and handled failures as one-line coded error records on
  standard error.
- **State:** no log or failure state is retained, persisted, queued, or sent.
- **Lifecycle:** validate launcher dependencies, load configuration, execute
  one command, translate a handled failure if necessary, and terminate.
- **Ownership:** the application entry point owns the current process boundary
  because there is one runtime and one command consumer.
- **Cancellation:** the current process owns no external resource or mutable
  state, so termination requires no retry, rollback, or cleanup protocol.

## Security and privacy

Error records contain only constants controlled by the application. They do
not include exception messages, causes, class names, stack traces, configured
values, command arguments, credentials, personal data, timestamps, hostnames,
paths, or environment contents. Tests inject private marker values into each
handled failure path and require that those markers are absent from output.

This conservative policy prevents accidental disclosure before KAOS has a real
diagnostic-storage or support workflow. The stable code and reproduction
command are the current troubleshooting evidence. Any later debug detail must
define explicit user control, redaction, destination, retention, and access
before it is added.

## Validation evidence

On 2026-08-19:

| Check | Result |
| --- | --- |
| Focused application/configuration/CLI/error tests | 23 passed |
| Successful real process | Exit 0; exact status on standard output |
| Expected usage failure | Exit 2; guidance on standard error; argument not echoed |
| Invalid configuration | Exit 1; exact `KAOS-CONFIG-001` record; value not echoed |
| Unreadable configuration | Focused test proves exit 1, `KAOS-CONFIG-002`, and non-disclosure |
| Unexpected loader failure | Focused test proves exit 1, `KAOS-APP-001`, and non-disclosure |
| Unexpected command failure | Focused test proves application classification and non-disclosure |
| JVM `Error` | Focused test proves it propagates instead of being converted |
| Full lifecycle | `clean test build check` passes |
| Development version | Remains `0.0.2-SNAPSHOT`; no tag or release created |

Canonical verification:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --args=status --no-daemon
```

## Feature 001.04 acceptance map

| Criterion | Evidence |
| --- | --- |
| One explicit outcome | Application failures become safe coded records and exit 1 instead of raw stack traces |
| Outcome works | Real invalid-configuration process plus focused failure-boundary tests |
| Failure and safety proportional | Three current failure codes, no raw details, no JVM `Error` masking, no state mutation |
| Focused validation | Twenty-three tests cover success, usage, configuration, runtime, streams, results, and disclosure |
| Documentation matches behavior | This record contains the exact codes, formats, results, recovery, ownership, and limits |
| No future architecture prerequisite | One JDK-only boundary in the existing entry point |
| Security, privacy, ownership, and control | Constant-only records, local streams, no retention or external destination |
| Current roadmap evidence is sufficient | Feature #841, Tasks #1055/#1056, source, tests, README, and this checkpoint |

## Limitations and handoff

KAOS has no debug mode, trace identifier, log levels beyond the current error
record, timestamps, structured serialization, log files, rotation, remote
collection, metrics, tracing, alerts, retry engine, or persistent incident
history. These are intentionally deferred until a real capability and
operational workflow demonstrate which diagnostics are useful and safe.

After the Feature 001.04 pull request merges, close #1056 and #841, delete the
feature branch, and activate Feature
[#844](https://github.com/karanbabu2110/KAOS/issues/844), Application Test
Harness. No Git tag or GitHub release is part of this handoff.
