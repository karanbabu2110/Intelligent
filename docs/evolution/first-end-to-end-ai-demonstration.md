# First End-to-End AI Demonstration

Feature [#851](https://github.com/karanbabu2110/KAOS/issues/851) and Task
[#1076](https://github.com/karanbabu2110/KAOS/issues/1076) prove the assembled
Epic 002 runtime once through the packaged KAOS application and one explicitly
selected installed Ollama model.

## Demonstrated path

```text
packaged KAOS command
  -> KaosApplication routing and configuration
  -> real OllamaPromptClient
  -> fixed http://127.0.0.1:11434
  -> installed qwen3:4b-instruct
  <- streamed NDJSON records
  -> validated answer output
```

This feature adds operational evidence, not another runtime boundary. The
production source, endpoint, dependencies, package ownership, configuration,
and verification lifecycle remain unchanged. Deterministic automated proof is
owned by Feature 002.06; this observation supplies the installed-provider fact
that a loopback fixture cannot prove.

## Reproduce the demonstration

Use Java 21 and a running local Ollama installation. Select an already installed
ordinary model explicitly; KAOS never discovers, downloads, or substitutes one.

```powershell
ollama list
./gradlew.bat clean installDist --no-daemon --warning-mode=all

$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "64"

& ".\build\install\KAOS\bin\KAOS.bat" ollama-status
& ".\build\install\KAOS\bin\KAOS.bat" ollama-model
& ".\build\install\KAOS\bin\KAOS.bat" ollama-prompt `
    "Reply with exactly this token and nothing else: KAOS_END_TO_END_OK"
```

The Gradle-generated Windows launcher enables command echo when a caller already
defines `DEBUG`. Exact-output capture therefore removed `DEBUG` only from the
child process environment; it did not modify the user's environment or KAOS.

## Observed result

Verified on 2026-08-30 with Java `21.0.11`, Ollama `0.32.1`, and installed model
`qwen3:4b-instruct` (`0edcdef34593`):

| Check | Observed result |
| --- | --- |
| Distribution | Existing `installDist` task succeeded and produced the application JAR, Jackson runtime JARs, and platform launchers |
| Provider status | `Local Ollama is reachable (version 0.32.1).`; exit `0`; empty stderr; 817 ms |
| Explicit model | `qwen3:4b-instruct`, 4,096-token context, thinking off, 64-token response bound; exit `0`; empty stderr; 141 ms |
| Prompt stdout | Exact `KAOS_END_TO_END_OK` followed by the platform newline |
| Prompt lifecycle | First validated output byte at 984 ms; clean process completion at 1,655 ms |
| Prompt result | Exit `0`; empty stderr; no thinking label or reasoning content |
| Network boundary | Existing production client permits only fixed loopback Ollama and disables redirects |

The first-output and completion times are one warm local observation, not a
performance guarantee or service-level objective. The 671 ms interval confirms
that application output was visible before process completion. The gated
loopback test remains the deterministic proof that answer output precedes the
terminal completion record.

## Deterministic and complete verification

The real-model observation is intentionally excluded from `test`, `build`, and
`verifyLocal`: installed models, local hardware, and generated text are not
deterministic build prerequisites. Feature 002.06 covers the same application,
client, HTTP, NDJSON, output, failure, and privacy seam with an ephemeral
loopback server.

```powershell
./gradlew.bat test --tests io.kaos.app.KaosOllamaIntegrationTest --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Task 002.07.01 passed all three focused integration scenarios, all 114 tests,
and all 11 `verifyLocal` tasks after its evidence and architecture updates.

## Safety and limitations

- The prompt is fixed, synthetic, and contains no secret or personal data.
- Prompts and generated data remain process-local and travel only to fixed
  loopback Ollama; KAOS does not persist or log them.
- The developer CLI prompt may remain in shell history or local process
  inspection, so it is not a secret-input surface.
- This run proves installed-model compatibility and the complete local request
  path, not general answer quality, throughput, portability, or hardware
  performance.
- There is still one foreground request with no conversation history, retry,
  resume, tools, persistence, remote provider, or background work.

## Epic 002 exit readiness

Features 002.01 through 002.06 provide reachability, explicit model policy,
prompt submission, streaming, bounded failure handling, and deterministic
integration proof. This final installed-model observation demonstrates their
combined outcome: one local prompt returns one validated streamed answer through
the single KAOS application.

Feature #851 and Epic
[#3](https://github.com/karanbabu2110/KAOS/issues/3) remain in progress until
review and merge. After that checkpoint, Conversation Capability Epic
[#9](https://github.com/karanbabu2110/KAOS/issues/9) is the next approved goal
under roadmap #814. No tag or release is part of this feature.
