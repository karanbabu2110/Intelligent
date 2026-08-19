# Application configuration

## Outcome

Feature [#840](https://github.com/karanbabu2110/KAOS/issues/840) adds the
smallest useful configuration capability to the single KAOS application: a
validated local application name that changes the real startup diagnostic.

The implementation is one immutable Java record in `io.kaos.app.config`. It
uses only the JDK and does not introduce a configuration framework, file,
network service, secret store, module, or process boundary.

## Configuration contract

| Priority | Source | Key | Example |
| ---: | --- | --- | --- |
| 1 | JVM system property | `kaos.app.name` | `Local KAOS` |
| 2 | Environment variable | `KAOS_APP_NAME` | `Local KAOS` |
| 3 | Built-in default | none | `KAOS` |

Presence controls precedence. An invalid higher-priority value fails instead
of silently falling back to a lower-priority source. This makes configuration
mistakes visible and reproducible.

The normalized name:

- is stripped of outer Unicode whitespace;
- must contain between 1 and 64 Unicode code points;
- may contain Unicode letters, numbers, and combining marks;
- may contain ordinary spaces, periods, underscores, and hyphens;
- rejects newlines, tabs, controls, separators, shell punctuation, and other
  characters that could make the startup diagnostic ambiguous or injectable.

## Run examples

Default:

```powershell
./gradlew.bat run --no-daemon
```

Environment override:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run --no-daemon
Remove-Item Env:KAOS_APP_NAME
```

System-property override for a direct JVM launch:

```powershell
./gradlew.bat classes --no-daemon
& java '-Dkaos.app.name=Property KAOS' `
  -cp build/classes/java/main `
  io.kaos.app.KaosApplication
```

When both sources exist, `kaos.app.name` wins. The resolved name is printed as:

```text
Local KAOS application baseline is running.
```

## Inputs, outputs, state, and lifecycle

- **Inputs:** one optional non-secret display name from the local process
  property/environment, otherwise the built-in default.
- **Output:** the existing one-line startup diagnostic with the validated name.
- **State:** an immutable in-memory record created during startup and discarded
  when `main` returns.
- **Lifecycle:** load, normalize, validate, print, and return; there is no
  reload, watcher, cache, retry, cancellation, or background work.
- **Ownership:** `io.kaos.app.config` owns application-process configuration;
  future capability-specific settings remain with their capability.

## Failure and recovery

Blank, oversized, or disallowed values raise `IllegalArgumentException` with
the failing source and constraint. A process permission failure while reading
local properties/environment is wrapped as `IllegalStateException` with an
actionable configuration message. The exception remains visible and the
process exits nonzero; Feature #841 will add the later user-facing error/logging
policy.

Recovery is correcting or removing the local value and restarting. No data,
resource, or external state can be left inconsistent.

## Security and privacy

This surface is explicitly **not** a secret channel. It reads only an
application display name and never prints an unvalidated value. It performs no
network, filesystem, credential, personal-data, destructive, or privileged
operation. Provider keys, tokens, passwords, remote endpoints, and file-backed
configuration are deferred until a feature has a real consumer and defines
redaction, storage, and failure behavior.

## Validation evidence

On 2026-08-19:

| Check | Result |
| --- | --- |
| Focused application/configuration tests | 11 passed |
| Safe default | Exact `KAOS application baseline is running.` output |
| Environment override | Exact `Local KAOS application baseline is running.` output |
| System-property precedence | Focused test and direct JVM launch pass |
| Invalid newline value | Process exits nonzero with the allowed-character constraint |
| Unicode name | `KAOS भारत` is accepted without permitting controls |
| Clean lifecycle | `clean test build check` passes |
| Development version | `0.0.2-SNAPSHOT`, distinct from released `v0.0.1` |

Canonical verification:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon
./gradlew.bat clean test build check --no-daemon --warning-mode=all
./gradlew.bat run --no-daemon
```

## Feature 001.02 acceptance map

| Criterion | Evidence |
| --- | --- |
| One demonstrable outcome | A local setting changes the actual application startup output |
| Outcome works from current evidence | Configuration record, real entry-point integration, 11 tests, and direct runs pass |
| Failure and safety are proportional | Invalid values fail visibly; no secret, data, external action, or persistent state exists |
| Focused validation proves acceptance | Default, precedence, trimming, Unicode, blank, oversized, injection, and startup cases are covered |
| Documentation matches behavior | This record provides exact sources, commands, validation, failure, and limitations |
| No unrelated future architecture | JDK-only package inside the existing root application; no framework or new boundary |
| Security, privacy, ownership, and control evaluated | Non-secret local input, validated before output, with application-package ownership |
| Current #814 evidence is sufficient | Feature #840, Tasks #1051/#1052, source, tests, README, and this checkpoint provide the proof |

## Limitations and handoff

The application does not yet support configuration files, typed settings
beyond the display name, secret loading, dynamic reload, profiles, remote
configuration, or CLI arguments. Those are not implied by this feature.

After the Feature 001.02 pull request merges, close #1052 and #840, delete the
feature branch, and activate Feature
[#842](https://github.com/karanbabu2110/KAOS/issues/842), Basic Command-Line
Interaction. No new Git tag or release is part of this handoff.
