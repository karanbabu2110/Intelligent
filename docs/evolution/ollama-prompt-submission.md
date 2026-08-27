# Ollama prompt submission

## Outcome

Feature [#847](https://github.com/karanbabu2110/KAOS/issues/847) lets one
developer submit one bounded prompt from the KAOS command line to one
explicitly configured local Ollama model and receive one complete generated
response. Task [#1065](https://github.com/karanbabu2110/KAOS/issues/1065)
implements the behavior; Task
[#1066](https://github.com/karanbabu2110/KAOS/issues/1066) owns this validation
and evidence.

Response Streaming belongs to Feature
[#848](https://github.com/karanbabu2110/KAOS/issues/848) and remains the next
approved step.

## Current contract

| Concern | Current behavior |
| --- | --- |
| Command | `ollama-prompt <prompt>` with exactly one quoted CLI argument |
| Model | Explicit `kaos.ollama.model` or `KAOS_OLLAMA_MODEL`; no default |
| Provider endpoint | Fixed `http://127.0.0.1:11434/api/generate` |
| Request | JSON `POST` containing `model`, `prompt`, and `stream: false` |
| Success | Exit `0`; one complete generated response on standard output |
| Prompt bound | 4,096 Unicode code points; blank and unsafe control input rejected |
| Response bounds | 1 MiB response body and 65,536 generated Unicode code points |
| Time bounds | Two-second connection establishment and five-minute complete request |
| Failure | Exit `1`; safe `KAOS-AI-002` recovery category on standard error |
| State | One synchronous request; nothing persisted by KAOS |
| Ownership | `io.kaos.ai.ollama` inside the single root application |

KAOS accepts only a successful JSON response with a textual `response` field
and `done: true`. It rejects redirects, non-JSON content, incomplete or
malformed JSON, blank or oversized output, and unsafe terminal control
characters. The client reads at most 1 MiB plus one byte, so the body limit is
enforced while reading rather than after an unbounded allocation.

## Run the behavior

Confirm that Ollama is running and choose one model already installed locally:

```powershell
ollama list
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
./gradlew.bat run --args=ollama-model
```

PowerShell must preserve the quoted application argument through the Gradle
batch wrapper:

```powershell
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

The POSIX-shell equivalent is:

```bash
export KAOS_OLLAMA_MODEL=qwen3:8b
./gradlew run --args='ollama-prompt "Why is the sky blue?"'
```

There is intentionally no automatic model selection, discovery, pull, or
download. A first request may be much slower while Ollama loads a model.

## Runtime and dependency decision

The flow remains a direct in-process call:

```text
terminal
  -> KaosApplication command routing and safe error boundary
  -> OllamaPrompt validation
  -> OllamaModelConfiguration
  -> OllamaPromptClient
  -> fixed loopback Ollama POST /api/generate
  <- one complete non-streamed JSON response
  <- generated text on stdout
```

`com.fasterxml.jackson.core:jackson-databind:2.22.1` is the first direct
production runtime library. It serializes prompt/model text without manual JSON
escaping and parses the provider response structurally. The resolved runtime
classpath also contains Jackson Core `2.22.1` and Jackson Annotations `2.22`.
This small JSON boundary is justified by current behavior; an Ollama SDK, AI
framework, provider-neutral interface, Gradle module, plugin, worker, separate
repository, or service is not.

## Privacy and failure boundaries

- The prompt and selected model are sent only to fixed HTTP loopback. Remote
  endpoints, redirects, authentication, TLS, and external providers are not
  supported.
- KAOS does not log or persist the prompt or generated response and never puts
  the prompt, raw provider body, exception detail, or configured model into an
  error.
- The developer CLI is not a secret-input surface. A prompt can remain in shell
  history and may be visible to local process inspection; private prompts need
  a later input mechanism designed for that requirement.
- No retry or background work occurs. Interruption restores the Java thread's
  interrupted flag, closes the response stream, and returns safe guidance.
- Timeouts are distinct from connection failure. They recommend retrying or
  selecting a faster local model without claiming Ollama is offline.

## Validation evidence

Verified on 2026-08-27:

| Check | Result |
| --- | --- |
| Focused prompt and application tests | Passed |
| Complete automated suite | 74 passed, 0 failed, 0 errors, 0 skipped |
| Real local provider | Ollama `0.32.1` on fixed `127.0.0.1:11434` |
| Installed model demonstration | `qwen3:8b` returned one complete answer through KAOS |
| Real request duration | 2 minutes 43 seconds; succeeded within the five-minute bound |
| Request inspection | `POST`, JSON content type, selected model, exact prompt, `stream: false` |
| Failure and privacy cases | Unavailable, timeout, rejection, invalid response, interruption, and invalid prompt return no prompt or raw provider data |
| Local-only boundary | Non-loopback endpoint construction is rejected; redirects are disabled |
| Response limits | Oversized body, oversized text, malformed JSON, incomplete response, and unsafe output are rejected |
| Runtime dependency | Jackson Databind `2.22.1`, Core `2.22.1`, and Annotations `2.22` only |
| Architecture website | Local files, anchors, IDs, accessibility markup, responsive CSS, UTF-8 content, and Feature 002.03 recency markers passed static checks; the page and stylesheet returned HTTP 200; desktop/mobile browser rendering could not run because no controllable browser was connected |

Commands:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.OllamaPromptTest' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
./gradlew.bat dependencies --configuration runtimeClasspath --no-daemon
```

The real demonstration used the PowerShell command above with
`KAOS_OLLAMA_MODEL=qwen3:8b`. Automated tests use an isolated loopback HTTP
server and do not require Ollama or an installed model.

## Known limitations

- Output is complete-only. There is no token streaming, progress indicator, or
  cancellation UI while a model is generating.
- The CLI supports one prompt and one response, not conversation history,
  system prompts, templates, tools, images, structured output, embeddings, or
  model options.
- The CLI argument may be retained by the shell or exposed to local process
  inspection.
- KAOS does not discover, install, pull, preload, unload, or persist models.
- There is no retry, backoff, provider-specific diagnostic display, metrics,
  tracing, remote endpoint, authentication, or provider-neutral abstraction.
- The five-minute request bound is a proportional development default, not a
  measured service-level objective.

## Acceptance map

| Criterion | Evidence |
| --- | --- |
| One demonstrable outcome | One quoted prompt produces one complete local model response |
| Explicit inputs and outputs | Bounded prompt plus configured model; generated text or coded safe failure |
| Safe failure behavior | Focused tests cover invalid input, dependency failure, timeout, rejection, invalid output, and interruption |
| Privacy and user control | Fixed loopback, explicit model, no prompt/error echo, no persistence or automatic download |
| Focused and complete verification | Isolated tests, clean `verifyLocal`, dependency report, and real Ollama demonstration |
| No premature architecture | One package, direct calls, one justified JSON library, no module or service |
| Documentation and architecture | README, developer guide, this evidence, and living architecture match source behavior |
| Deferred work explicit | Streaming remains Feature #848 and is not treated as implemented |

## Handoff

After the single Feature 002.03 pull request is merged, close Tasks
[#1065](https://github.com/karanbabu2110/KAOS/issues/1065)-[#1066](https://github.com/karanbabu2110/KAOS/issues/1066)
and Feature [#847](https://github.com/karanbabu2110/KAOS/issues/847). Keep Epic
[#3](https://github.com/karanbabu2110/KAOS/issues/3) active and activate Response
Streaming Feature [#848](https://github.com/karanbabu2110/KAOS/issues/848) next.
Do not create a tag or release without an explicit user request.
