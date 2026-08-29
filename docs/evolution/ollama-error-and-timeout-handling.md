# Ollama Error and Timeout Handling

Feature [#849](https://github.com/karanbabu2110/KAOS/issues/849) and Task
[#1074](https://github.com/karanbabu2110/KAOS/issues/1074) make one local Ollama
failure actionable: KAOS identifies the failed request phase, provides bounded
recovery guidance, and never exposes private request or provider details.

## Implemented outcome

`OllamaPromptClient` owns the lifecycle from the fixed loopback request through
the terminal NDJSON record. `KaosApplication` owns the terminal diagnostic and
exit code. Both remain direct in-process parts of the single Java application.

| Outcome | Detection boundary | Application diagnostic |
| --- | --- | --- |
| Unavailable | The HTTP request fails before response headers arrive | `KAOS-AI-001`; start local Ollama |
| Request failed | Ollama returns a non-success HTTP status | `KAOS-AI-002`; verify the configured model |
| Invalid response | Content type, UTF-8, NDJSON, ordering, terminal reason, metrics, or generated content is invalid | `KAOS-AI-002`; verify local Ollama |
| Stream failed | Transport fails after Ollama accepted the request | `KAOS-AI-004`; verify Ollama remains running |
| Total timeout | The complete prompt lifecycle reaches five minutes | `KAOS-AI-005`; shorten the request or select a faster model |
| Inactivity timeout | The accepted stream emits no data for 60 seconds | `KAOS-AI-005`; check provider progress or select a faster model |
| Provider limit | Ollama completes with `done_reason: length` | `KAOS-AI-003`; review response and context limits |
| Local limit | KAOS reaches its byte, answer, or thinking bound | `KAOS-AI-003`; shorten the request or response |
| Cancelled | The command thread is interrupted | `KAOS-AI-006`; retry only when ready |

All non-success outcomes return application exit code `1`. Validated chunks may
already be visible when a stream fails. KAOS completes the stdout line, labels
that output partial on stderr, cancels the subscription, and does not retry.

## Safety and ownership

- The endpoint remains fixed to loopback HTTP; no remote provider is added.
- Diagnostics contain no prompt, generated reasoning, raw provider body,
  configured private value, stack trace, or exception message.
- Provider rejection and invalid data intentionally share a safe diagnostic
  family because raw provider detail is not needed for current recovery.
- Total and inactivity deadlines remain fixed implementation safety bounds, not
  user configuration.
- No retry, resume, conversation state, provider-neutral interface, framework,
  module, worker, event bus, or service is introduced.

## Deterministic verification

`OllamaPromptClientTest` uses loopback servers to reproduce unavailability,
non-success responses, truncated accepted streams, total deadline expiry,
inactivity expiry, invalid streams, limits, and interruption without requiring
a running Ollama installation. `KaosApplicationTest` verifies exact diagnostic
codes, guidance, partial-output labeling, exit behavior, and private-data
exclusion.

Run the focused checkpoint:

```powershell
./gradlew.bat test --tests io.kaos.ai.ollama.OllamaPromptClientTest --tests io.kaos.app.KaosApplicationTest --no-daemon --warning-mode=all
```

Run the complete checkpoint before completing the feature:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Actual result on 2026-08-29: the focused checkpoint passed 62 tests. The
from-clean-state checkpoint then passed all 111 tests and all 11
`verifyLocal` tasks, including packaging plus deterministic `status` and `help`
smoke commands.

## Limitations and next checkpoint

KAOS still performs one foreground request and has no automatic retry, resume,
conversation state, remote provider, configurable timeout policy, or background
work. Feature #849 was completed and merged through
[PR #23](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/pull/23).
Feature [#850](https://github.com/karanbabu2110/KAOS/issues/850) now owns the
deterministic application-to-provider integration evidence.
