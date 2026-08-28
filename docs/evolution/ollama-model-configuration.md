# Ollama model configuration

## Outcome

Feature [#846](https://github.com/karanbabu2110/KAOS/issues/846) lets KAOS
resolve, validate, and display one explicitly selected local Ollama model plus
its bounded context window, explicit thinking mode, and response-token limit. The `ollama-model`
command proves the configuration without contacting Ollama.

Prompt submission is implemented by Feature
[#847](https://github.com/karanbabu2110/KAOS/issues/847). Response streaming
remains Feature [#848](https://github.com/karanbabu2110/KAOS/issues/848) and is
not implemented.

## Current contract

| Concern | Current behavior |
| --- | --- |
| Command | `ollama-model` |
| System property | `kaos.ollama.model` |
| Environment variable | `KAOS_OLLAMA_MODEL` |
| Precedence | System property, then environment variable |
| Default | None; selection is explicit |
| Context system property | `kaos.ollama.context-window` |
| Context environment variable | `KAOS_OLLAMA_CONTEXT_WINDOW` |
| Context precedence | System property, environment variable, 4,096-token default |
| Context range | 2,048 through 65,536 whole tokens |
| Thinking system property | `kaos.ollama.thinking` |
| Thinking environment variable | `KAOS_OLLAMA_THINKING` |
| Thinking precedence | System property, environment variable, `off` |
| Thinking values | `off` or `on`; no `auto` or provider levels |
| Response-limit system property | `kaos.ollama.response-token-limit` |
| Response-limit environment variable | `KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT` |
| Response-limit precedence | System property, environment variable, mode-specific default |
| Response-limit defaults | 512 tokens when thinking is off; 2,048 when thinking is on |
| Response-limit range | 64 through 4,096 whole tokens |
| Success | Exit `0`; validated model, context, thinking mode, and response-token limit on standard output |
| Invalid or missing | Exit `1`; `KAOS-AI-CONFIG-001` with constant guidance |
| Unreadable process configuration | Exit `1`; `KAOS-AI-CONFIG-002` with constant guidance |
| Provider request | None |
| State | Process configuration only; nothing persisted |
| Ownership | `io.kaos.ai.ollama` in the root application |

The model name is trimmed and limited to 128 ASCII characters. It accepts
ordinary or slash-separated segments containing letters, numbers, periods,
underscores, or hyphens, followed by an optional colon tag. This bounded form
supports examples such as `qwen3:8b` and `hf.co/team/model-name:Q4_K_M` while
rejecting whitespace, control characters, empty segments, and diagnostic
injection.

## Run the behavior

For the Gradle workflow, set the environment variable:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat run --args=ollama-model --no-daemon
```

Expected output:

```text
Configured local Ollama model: qwen3:4b-instruct (context window: 4096 tokens, thinking: off, response limit: 512 tokens).
```

A direct JVM launch can give the system property precedence:

```powershell
./gradlew.bat classes --no-daemon
java -Dkaos.ollama.model=qwen3:4b-instruct -Dkaos.ollama.thinking=off -Dkaos.ollama.response-token-limit=512 -cp build/classes/java/main io.kaos.app.KaosApplication ollama-model
```

If no model is configured, KAOS returns constant recovery guidance without
guessing a model or printing any configured value.

## Inputs, state, lifecycle, and ownership

- **Input:** local model plus optional context-window, thinking, and
  response-token-limit process settings; none is accepted as a CLI argument.
- **Output:** the validated model name, context window, thinking mode, and
  response-token limit or a safe coded error.
- **Lifecycle:** configuration is loaded lazily only for `ollama-model` and
  `ollama-prompt`.
- **State:** no model choice is written to disk or retained after process exit.
- **User control:** KAOS selects no default model, uses the evidence-selected
  4,096-token ordinary context, `off` thinking, and a mode-specific bounded
  response default unless overridden, and initiates no download.
- **Provider interaction:** none; a running Ollama server is not required.
- **Ownership:** model selection remains beside Ollama connectivity because
  there is one provider and one current consumer.

Existing `status`, `help`, and `ollama-status` behavior does not require model
configuration. No interface, shared configuration framework, Gradle module,
plugin, repository, worker, or service is justified.

The context decision and repeatable measurements are recorded in the
[Ollama context-window benchmark](ollama-context-window-benchmark.md).
The separate [model scenario benchmark](ollama-model-scenario-benchmark.md)
recommends explicit smoke-test, ordinary, and reasoning profiles without
turning any recommendation into an application default or fallback.
The [thinking policy and benchmark](ollama-thinking-policy-and-benchmark.md)
selects explicit boolean `off` and `on` behavior from comparative evidence.
The [response-generation limit benchmark](ollama-response-generation-limit-benchmark.md)
selects the 512/2,048 defaults, 64-4,096 override range, and completion policy.

## Security and privacy

The command intentionally displays a successfully validated model name. Errors
never echo missing or invalid configured values, exception messages, stack
traces, environment contents, prompts, credentials, tokens, files, or personal
data. No provider request, telemetry, persistence, or model installation occurs.

## Validation evidence

Verified on 2026-08-28:

| Check | Result |
| --- | --- |
| Complete automated suite | 97 passed, 0 failed |
| Environment selection | Ordinary 512-token and reasoning 2,048-token defaults displayed through real Gradle runs |
| System-property precedence | Focused test and direct JVM demonstration passed |
| Missing selection | Safe `KAOS-AI-CONFIG-001`; exit `1` |
| Invalid selection | Safe `KAOS-AI-CONFIG-001`; configured value not returned |
| Unreadable configuration | Safe `KAOS-AI-CONFIG-002`; exception detail not returned |
| Lazy loading | Existing commands pass without model configuration |
| Clean aggregate workflow | 11 tasks executed; build, 97 tests, package, status, and help passed |
| Production dependencies | Context work adds none; the existing prompt JSON dependency is unchanged |

Commands:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.OllamaModelConfigurationTest' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
./gradlew.bat dependencies --configuration runtimeClasspath --no-daemon
```

## Known limitations

- KAOS does not check whether the configured model is installed or available.
- No default model, model discovery, download, installation, loading, or pull
  confirmation exists.
- Scenario recommendations are developer guidance only. KAOS does not inspect
  the prompt, route between profiles, or replace an unavailable selection.
- Thinking defaults to `off`; `on` requires an explicit choice. `auto` and
  provider-specific level configuration are not implemented.
- Response generation is always bounded. KAOS rejects unbounded, below-range,
  and excessive limits and never expands a truncated request automatically.
- The 4K context default is selected for current ordinary prompts on the
  measured development machine. It is not a promise that every future RAG,
  conversation, coding, or agent workload fits in 4K.
- Prompt submission exists, but streaming, retry, remote endpoint,
  authentication, TLS, model discovery, and general model-specific profiles do
  not.
- The supported model-name syntax is a deliberate safe subset, not a claim to
  accept every provider-specific identifier.
- The configuration is local process state and is not persisted by KAOS.

## Acceptance map

| Criterion | Evidence |
| --- | --- |
| One demonstrable outcome | `ollama-model` displays one explicit validated selection |
| Deterministic precedence | Model, context, thinking, and response-limit properties override their environment values; bounded defaults apply where documented |
| Safe failures | Missing, invalid, and unreadable settings return coded constant guidance |
| Focused verification | Configuration and application tests cover valid and failure paths |
| Complete verification | Clean `verifyLocal` passes all 97 tests and application smoke checks |
| No future architecture prerequisite | Existing package and direct call; no new runtime dependency or stronger boundary |
| User control and privacy | Explicit model/context/thinking/response-limit control, no automatic download or expansion, no persistence, safe errors |
| #814-only evidence | Feature #846, Tasks #1063-#1064 and #1067-#1070, source, tests, benchmarks, and current documentation |

## Handoff

Feature 002.02 has been reopened from real prompt-performance evidence. Task
[#1067](https://github.com/karanbabu2110/KAOS/issues/1067) owns the context
decision and [#1068](https://github.com/karanbabu2110/KAOS/issues/1068) owns
the measured scenario recommendations.
[#1069](https://github.com/karanbabu2110/KAOS/issues/1069) owns explicit
thinking behavior and
[#1070](https://github.com/karanbabu2110/KAOS/issues/1070) owns bounded
response generation. Response
streaming remains Feature
[#848](https://github.com/karanbabu2110/KAOS/issues/848).
