# Basic command-line interaction

## Outcome

Feature [#842](https://github.com/karanbabu2110/KAOS/issues/842) adds a
small, one-shot command-line surface to the single KAOS application. A local
user can request status or usage help and receives deterministic output and an
explicit process result.

The behavior remains in the existing `KaosApplication` entry point. It uses
only the JDK and introduces no command framework, interactive shell, module,
service, repository, network call, file access, or persistent state.

## Command contract

| Arguments | Standard output | Standard error | Application exit code |
| --- | --- | --- | ---: |
| none | Configured application status | empty | 0 |
| `status` | Configured application status | empty | 0 |
| `help` | Usage and supported commands | empty | 0 |
| `--help` | Usage and supported commands | empty | 0 |
| one unknown command | empty | Safe unknown-command guidance | 2 |
| two or more arguments | empty | Safe argument-count guidance | 2 |

The no-argument path remains compatible with the earlier application
baseline. Commands are exact and case-sensitive. Exit code `2` represents a
command usage error; Feature #841 will define the broader application error
and logging policy.

## Run examples

Default status:

```powershell
./gradlew.bat run --no-daemon
```

Explicit status:

```powershell
./gradlew.bat run --args=status --no-daemon
```

Help:

```powershell
./gradlew.bat run --args=help --no-daemon
```

The help output is:

```text
Usage: kaos [status|help]

Commands:
  status  Show local application status (default).
  help    Show this help. The --help alias is also supported.
```

An invalid command makes Gradle report that the application process failed.
To inspect the application's exact exit code directly:

```powershell
./gradlew.bat classes --no-daemon
& java -cp build/classes/java/main io.kaos.app.KaosApplication unknown
$LASTEXITCODE
```

The last command prints `2`. The diagnostic is:

```text
Unknown command. Run 'kaos help' for usage.
```

## Configuration interaction

The `status` and no-argument paths use the application name delivered by
Feature #840. The existing source precedence and validation remain unchanged:
`kaos.app.name`, then `KAOS_APP_NAME`, then `KAOS`.

For example:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run --args=status --no-daemon
Remove-Item Env:KAOS_APP_NAME
```

This prints `Local KAOS application baseline is running.` Invalid
configuration still fails during startup before command execution; the
user-facing presentation of that failure belongs to Feature #841.

## Inputs, outputs, state, and lifecycle

- **Inputs:** zero or more local process arguments plus the already validated
  application configuration.
- **Outputs:** successful command content on standard output; usage failures on
  standard error.
- **Result:** `0` for a recognized command and `2` for invalid command syntax.
- **State:** no command state is retained or persisted.
- **Lifecycle:** load configuration, inspect arguments once, print one result,
  and return or exit; there is no prompt, loop, retry, cancellation window, or
  background work.
- **Ownership:** the application entry point owns this bootstrap CLI until a
  real second command consumer demonstrates a need for extraction.

## Failure, recovery, security, and privacy

Unknown commands and extra arguments fail before performing any capability
action. Diagnostics deliberately do not echo argument values, so a token or
private value accidentally supplied as an argument is not repeated in logs or
terminal history by KAOS. The process reads no credentials or personal data
and performs no network, filesystem, privileged, destructive, or external
action.

Recovery is running `kaos help`, correcting the command, and starting a new
process. Because execution is read-only and stateless, failure cannot leave
application or external state inconsistent. Cancellation has no special
cleanup requirement because the process owns no resource and performs no
background work.

## Validation evidence

On 2026-08-19:

| Check | Result |
| --- | --- |
| Focused application/configuration/CLI tests | 17 passed |
| No arguments | Exit 0; exact configured status on standard output |
| `status` | Exit 0; exact configured status on standard output |
| `help` and `--help` | Exit 0; exact documented usage on standard output |
| Unknown command | Exit 2; safe guidance on standard error; input not echoed |
| Extra argument | Exit 2; safe guidance on standard error; input not echoed |
| Configured status | Exit 0; exact `Local KAOS` diagnostic |
| Full lifecycle | `clean test build check` passes |
| Development version | Remains `0.0.2-SNAPSHOT`; no tag or release created |

Canonical verification:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --args=status --no-daemon
./gradlew.bat run --args=help --no-daemon
```

## Feature 001.03 acceptance map

| Criterion | Evidence |
| --- | --- |
| One explicit outcome | A local user can request status or help through the real entry point |
| Outcome works | Focused tests and direct process runs prove each supported command |
| Failure and safety are proportional | Invalid shapes return 2 on standard error without echoing values or changing state |
| Focused and application validation | Seventeen tests plus direct exit/output checks and the clean build |
| Documentation matches behavior | This record contains the exact syntax, streams, results, recovery, and limitations |
| No future architecture prerequisite | Logic remains in one JDK-only application class |
| Security, privacy, ownership, and control | Local non-secret inputs, no external action, safe diagnostics, application ownership |
| Current roadmap evidence is sufficient | Feature #842, Tasks #1053/#1054, source, tests, README, and this checkpoint |

## Limitations and handoff

This is deliberately not an interactive shell. It has no prompt, history,
completion, command chaining, subcommand tree, dynamic discovery, AI input,
files, remote calls, or persistent session. Those capabilities require later
user outcomes rather than speculative CLI infrastructure.

After the Feature 001.03 pull request merges, close #1054 and #842, delete the
feature branch, and activate Feature
[#841](https://github.com/karanbabu2110/KAOS/issues/841), Minimal Error Handling
and Logging. No Git tag or GitHub release is part of this handoff.
