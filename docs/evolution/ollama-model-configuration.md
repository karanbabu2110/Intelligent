# Ollama model configuration

## Outcome

Feature [#846](https://github.com/karanbabu2110/KAOS/issues/846) lets KAOS
resolve, validate, and display one explicitly selected local Ollama model. The
`ollama-model` command proves the selection without contacting Ollama.

Prompt submission belongs to Feature
[#847](https://github.com/karanbabu2110/KAOS/issues/847), and response streaming
belongs to Feature [#848](https://github.com/karanbabu2110/KAOS/issues/848).
Neither behavior is implemented here.

## Current contract

| Concern | Current behavior |
| --- | --- |
| Command | `ollama-model` |
| System property | `kaos.ollama.model` |
| Environment variable | `KAOS_OLLAMA_MODEL` |
| Precedence | System property, then environment variable |
| Default | None; selection is explicit |
| Success | Exit `0`; validated model name on standard output |
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
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
./gradlew.bat run --args=ollama-model --no-daemon
```

Expected output:

```text
Configured local Ollama model: qwen3:8b.
```

A direct JVM launch can give the system property precedence:

```powershell
./gradlew.bat classes --no-daemon
java -Dkaos.ollama.model=qwen3:8b -cp build/classes/java/main io.kaos.app.KaosApplication ollama-model
```

If no model is configured, KAOS returns constant recovery guidance without
guessing a model or printing any configured value.

## Inputs, state, lifecycle, and ownership

- **Input:** one local process setting; it is not accepted as a CLI argument.
- **Output:** the validated model name or a safe coded error.
- **Lifecycle:** configuration is loaded lazily only for `ollama-model`.
- **State:** no model choice is written to disk or retained after process exit.
- **User control:** KAOS selects no default and initiates no model download.
- **Provider interaction:** none; a running Ollama server is not required.
- **Ownership:** model selection remains beside Ollama connectivity because
  there is one provider and one current consumer.

Existing `status`, `help`, and `ollama-status` behavior does not require model
configuration. No interface, shared configuration framework, Gradle module,
plugin, repository, worker, or service is justified.

## Security and privacy

The command intentionally displays a successfully validated model name. Errors
never echo missing or invalid configured values, exception messages, stack
traces, environment contents, prompts, credentials, tokens, files, or personal
data. No provider request, telemetry, persistence, or model installation occurs.

## Validation evidence

Verified on 2026-08-27:

| Check | Result |
| --- | --- |
| Complete automated suite | 52 passed, 0 failed |
| Environment selection | `qwen3:8b` displayed; Gradle run succeeded |
| System-property precedence | Focused test and direct JVM demonstration passed |
| Missing selection | Safe `KAOS-AI-CONFIG-001`; exit `1` |
| Invalid selection | Safe `KAOS-AI-CONFIG-001`; configured value not returned |
| Unreadable configuration | Safe `KAOS-AI-CONFIG-002`; exception detail not returned |
| Lazy loading | Existing commands pass without model configuration |
| Clean aggregate workflow | 11 tasks executed; build, 52 tests, package, status, and help passed |
| Production dependencies | Runtime classpath reports no dependencies |

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
- No prompt, request payload, generated response, streaming, retry, remote
  endpoint, authentication, TLS, or model-specific options exist.
- The supported model-name syntax is a deliberate safe subset, not a claim to
  accept every provider-specific identifier.
- The configuration is local process state and is not persisted by KAOS.

## Acceptance map

| Criterion | Evidence |
| --- | --- |
| One demonstrable outcome | `ollama-model` displays one explicit validated selection |
| Deterministic precedence | Property overrides environment; no fallback default |
| Safe failures | Missing, invalid, and unreadable settings return coded constant guidance |
| Focused verification | Configuration and application tests cover valid and failure paths |
| Complete verification | Clean `verifyLocal` passes all 52 tests and application smoke checks |
| No future architecture prerequisite | Existing package, direct call, no runtime dependency or provider request |
| User control and privacy | Explicit selection, no download, no prompt, no persistence, safe errors |
| #814-only evidence | Feature #846, Tasks #1063-#1064, source, tests, and current documentation |

## Handoff

After the single Feature 002.02 pull request is merged, close Tasks
[#1063](https://github.com/karanbabu2110/KAOS/issues/1063)-[#1064](https://github.com/karanbabu2110/KAOS/issues/1064)
and Feature [#846](https://github.com/karanbabu2110/KAOS/issues/846). Then
activate Prompt Submission Feature
[#847](https://github.com/karanbabu2110/KAOS/issues/847) and create its stories
or direct tasks just in time. Do not implement prompt submission or response
streaming as part of this feature.
