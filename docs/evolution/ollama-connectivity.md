# Ollama connectivity

## Outcome

Feature [#845](https://github.com/karanbabu2110/KAOS/issues/845) adds the first
AI integration boundary to the single KAOS application. The new
`ollama-status` command performs one bounded request to a fixed local Ollama
version endpoint and returns either a validated version or safe recovery
guidance.

This is connectivity only. Model configuration, prompt submission, response
streaming, and comprehensive AI error/timeout behavior remain assigned to later
Epic 002 features.

## Current contract

| Concern | Current behavior |
| --- | --- |
| Command | `ollama-status` |
| Endpoint | Fixed `http://127.0.0.1:11434/api/version` |
| Request | One HTTP `GET` accepting JSON |
| Success | Exit `0`; validated version on standard output |
| Failure | Exit `1`; `KAOS-AI-001` and constant recovery guidance on standard error |
| Connection bound | 2 seconds |
| Request bound | 3 seconds |
| State | None retained or persisted |
| Runtime library | Java 21 `java.net.http.HttpClient`; no third-party dependency |
| Ownership | `io.kaos.ai.ollama` inside the root application |

The version response must be a small JSON object containing only a safe
`version` string of at most 64 letters, numbers, periods, underscores, pluses,
or hyphens. Raw response bodies and exception details are never returned.

## Run the behavior

Start Ollama locally, then run:

```powershell
./gradlew.bat run --args=ollama-status --no-daemon
```

Successful output follows this form:

```text
Local Ollama is reachable (version <validated-version>).
```

If Ollama is stopped or unreachable, KAOS emits:

```text
ERROR [KAOS-AI-001] Local Ollama is unavailable. Start Ollama on 127.0.0.1:11434 and retry.
```

An invalid response and an interrupted check use the same stable diagnostic
code with their own constant recovery message. Gradle reports a failed `run`
task when the application exits nonzero; use a direct Java launch when the
exact process exit code must be inspected.

## Inputs, outputs, lifecycle, and recovery

- **Input:** command selection only; there is no model, prompt, credential,
  endpoint, or body input.
- **Output:** a validated version or one safe coded failure record.
- **Lifecycle:** construct the client lazily for `ollama-status`, send one local
  request, classify the result, close no owned long-lived resource, and exit.
- **State:** no response, provider data, retry state, or connection is retained.
- **Cancellation:** interruption is preserved on the current thread and returns
  safe recovery guidance.
- **Recovery:** start or repair local Ollama and retry manually. KAOS does not
  start, stop, configure, or retry the Ollama process.

## Security and privacy

The production endpoint is restricted to loopback HTTP. Redirects are disabled.
The request contains only the HTTP method and an `Accept: application/json`
header. It sends no prompt, model, credential, token, user content, personal
data, file content, application configuration, or telemetry.

Failures may repeat the fixed loopback address as constant recovery guidance,
but they do not expose a user-supplied or remote endpoint, raw provider
response, exception message, stack trace, environment contents, or other
provider data. Tests place private marker values in provider responses and
require that the returned result does not contain them.

## Implementation structure

```text
io.kaos.app.KaosApplication
  -> io.kaos.ai.ollama.OllamaConnectivity
       -> Java HttpClient
            -> local Ollama /api/version
```

The application creates the Ollama client only for `ollama-status`. The
capability is a package in the root Gradle project and is called directly
in-process. There is one provider and one consumer, so no provider-neutral
interface, module, plugin, repository, worker, or service is justified.

## Validation evidence

Verified on 2026-08-20:

| Check | Result |
| --- | --- |
| Focused connectivity and application suite | 39 passed, 0 failed |
| Valid loopback response | Version parsed and returned safely |
| Non-success response | Safe unavailable result; body not returned |
| Malformed response | Safe invalid-response result; body not returned |
| Closed local port | Safe unavailable result |
| Delayed response | Request returned within the configured test bound |
| Interrupted request | Interrupt flag preserved; no private detail returned |
| Real local demonstration | Ollama `0.32.1` reported; process exit `0` |
| Clean aggregate workflow | 11 tasks executed; build, 39 tests, package, status, help passed |
| Production dependencies | No third-party runtime library added |

Commands:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.*' --tests 'io.kaos.app.*' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
./gradlew.bat classes --no-daemon
java -cp build/classes/java/main io.kaos.app.KaosApplication ollama-status
```

## Known limitations

- The endpoint is intentionally fixed and local-only.
- Only the current minimal version-response shape is accepted.
- The aggregate `verifyLocal` workflow does not require Ollama and therefore
  does not smoke-test `ollama-status`.
- No model is selected and no prompt or response is transferred.
- No retry, configurable timeout, richer diagnostic taxonomy, streaming,
  monitoring, remote access, authentication, or TLS behavior exists.
- Automated tests prove loopback HTTP behavior on the verified Windows/Java 21
  host; they do not claim a cross-platform matrix or provider-version matrix.

## Feature 002.01 acceptance map

| Criterion | Evidence |
| --- | --- |
| One explicit demonstrable outcome | `ollama-status` proves local Ollama reachability and version |
| Outcome works | Deterministic loopback tests and real Ollama 0.32.1 demonstration pass |
| Failure and safety are proportional | Unavailable, non-success, malformed, delayed, and interrupted paths return bounded safe results |
| Focused and application validation | 39 tests plus the clean aggregate workflow pass |
| Documentation matches behavior | README, developer guide, architecture website, and this record describe current limits |
| No future architecture prerequisite | One package, direct in-process call, JDK HTTP client, no third-party runtime dependency |
| Security, privacy, data ownership, user control | Loopback-only GET, no sensitive request data, no retained state, manual recovery |
| #814-only evidence | Feature #845, Tasks #1061-#1062, source, tests, and current documentation |

## Handoff

After the single Feature 002.01 pull request is merged, close Tasks
[#1061](https://github.com/karanbabu2110/KAOS/issues/1061)-[#1062](https://github.com/karanbabu2110/KAOS/issues/1062)
and Feature [#845](https://github.com/karanbabu2110/KAOS/issues/845). Then review
Feature [#846](https://github.com/karanbabu2110/KAOS/issues/846) and create its
stories or direct tasks just in time. Do not implement model configuration as
part of this feature.
