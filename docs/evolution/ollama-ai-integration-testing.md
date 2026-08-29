# Ollama AI Integration Testing

Feature [#850](https://github.com/karanbabu2110/KAOS/issues/850) and Task
[#1075](https://github.com/karanbabu2110/KAOS/issues/1075) add deterministic
evidence across the complete local application-to-provider seam without
requiring a running Ollama installation.

## Verified integration path

```text
ollama-prompt command
  -> KaosApplication routing and model selection
  -> real OllamaPromptClient
  -> ephemeral loopback HTTP server
  -> streamed NDJSON records
  -> validated answer callbacks
  -> exact stdout, stderr, and exit code
```

`KaosOllamaIntegrationTest` owns the seam-crossing scenarios. The test-only
`OllamaPromptClientTestSupport` constructs a bounded client for an ephemeral
loopback endpoint through the existing package-private constructor. Production
continues to use only the fixed `127.0.0.1:11434` endpoint, and no production
API or behavior changes.

## Deterministic scenarios

| Scenario | Evidence |
| --- | --- |
| Successful streamed answer | The application returns exit `0`, writes the assembled answer exactly once, and leaves stderr empty |
| Request configuration | The loopback server receives `POST`, NDJSON acceptance, the selected model, prompt, `stream: true`, `think: false`, `num_ctx`, and `num_predict` |
| Unavailable provider | A closed ephemeral loopback port reaches the application as safe `KAOS-AI-001` guidance |
| Partial malformed stream | A validated prefix reaches stdout; malformed private provider content produces partial-output `KAOS-AI-002` guidance without disclosure |
| Detailed stream validation | `OllamaPromptClientTest` retains split UTF-8, ordering, metrics, boundaries, timeout, transport, and cancellation coverage |
| Application policy | `KaosApplicationTest` retains command, configuration, diagnostics, thinking presentation, and private-data exclusion coverage |

All tests use local process memory and loopback networking. They require no
model download, installed Ollama process, remote endpoint, credential, secret,
container, database, filesystem fixture, or persistent state.

## Verification

Run only the seam-crossing suite:

```powershell
./gradlew.bat test --tests io.kaos.app.KaosOllamaIntegrationTest --no-daemon --warning-mode=all
```

Run the complete repository checkpoint:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

The integration class remains in the standard JUnit test source set, so
`test`, `check`, `build`, and `verifyLocal` include it automatically. A new
source set or Gradle task would duplicate the existing lifecycle without solving
a current isolation or execution problem.

Actual result on 2026-08-29: all three seam-crossing scenarios passed. The
from-clean-state checkpoint then passed all 114 tests and all 11
`verifyLocal` tasks, including packaging plus deterministic `status` and `help`
smoke commands.

## Safety, limitations, and next checkpoint

The loopback server is a controlled protocol fixture, not an Ollama emulator and
not evidence of real model quality, installed-model compatibility, latency, or
hardware behavior. Feature
[#851](https://github.com/karanbabu2110/KAOS/issues/851) owns the first real
installed-model end-to-end demonstration. Feature #850 remains in progress until
its complete review and feature pull request are finished.
