# KAOS

KAOS is one evolving Java application, delivering one useful goal at a time.
The development-model reset is complete; capabilities now grow incrementally
inside the verified single application.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Completed epics: [Epic 000 — Development Model Reset](https://github.com/karanbabu2110/KAOS/issues/815) and [Epic 001 — Minimal KAOS Application](https://github.com/karanbabu2110/KAOS/issues/2)
- Active epic: [Epic 002 — First AI Integration](https://github.com/karanbabu2110/KAOS/issues/3)
- Completed Epic 002 features: [002.01 — Ollama Connectivity](https://github.com/karanbabu2110/KAOS/issues/845), [002.02 — AI Model Configuration](https://github.com/karanbabu2110/KAOS/issues/846), [002.03 — Prompt Submission](https://github.com/karanbabu2110/KAOS/issues/847), and [002.04 — Response Streaming](https://github.com/karanbabu2110/KAOS/issues/848)
- Active feature: [002.05 — AI Error and Timeout Handling](https://github.com/karanbabu2110/KAOS/issues/849), with active Task [002.05.01 — Classify and Report Ollama Failures](https://github.com/karanbabu2110/KAOS/issues/1074)
- Repository state: one root Gradle/Java 21 application with one production entry point, optional loopback Ollama connectivity, explicit local model selection, an evidence-selected configurable context window, one bounded streaming prompt flow, and one JSON runtime library
- Completed features, stories, tasks, and verified evidence: [completed work and evidence](docs/evolution/completed-work-and-evidence.md)

## Architecture

The [living KAOS architecture website](ui/architecture/index.html) shows the
verified current runtime, the next approved capability, future capability
candidates, and the evidence required before introducing modules or services.
It is a structured, buildless UI that can grow into multiple pages or an
application when real complexity justifies that evolution. See its
[local run and maintenance guide](ui/architecture/README.md).

Every feature pull request that changes packages, dependencies, integrations,
data ownership, or runtime boundaries must update the diagram. Implemented and
planned elements must remain visually distinct.

## Developer guide

Start with the [KAOS developer guide](docs/development/developer-guide.md) for
prerequisites, setup, application commands, focused and complete test commands,
build outputs, troubleshooting, and the feature delivery workflow.

## Version

The latest published release is **0.1.0**, available from tag
[`v0.1.0`](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/releases/tag/v0.1.0).

That snapshot is the first useful local-AI development release: it can validate a local
Ollama server, select and bound a model configuration, and submit one safe
non-streamed prompt. It is not production-ready and has no stable public API,
persistent-data schema, or compatibility guarantee. See the
[0.1.0 release notes](docs/releases/v0.1.0.md). Current development after the
release adds bounded response streaming and phase-specific failure handling;
no later tag or release has been created.

## Run the application

```powershell
./gradlew.bat run --args=status
```

The no-argument form remains supported. Both forms print
`KAOS application baseline is running.` and exit successfully. Run
`./gradlew.bat run --args=help` for the exact supported syntax. Unknown
commands or extra arguments produce safe guidance and a nonzero result without
echoing the supplied values.

Check whether Ollama is reachable on the fixed local endpoint
`http://127.0.0.1:11434/api/version`:

```powershell
./gradlew.bat run --args=ollama-status
```

A successful check prints only the validated Ollama version. The command sends
no prompt, model name, credential, personal data, or file content. If Ollama is
unavailable or its version response is invalid, KAOS returns a safe
`KAOS-AI-001` error with recovery guidance.

Select and inspect the model that later AI commands will use:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat run --args=ollama-model
```

KAOS requires an explicit selection and does not assume or download a default
model. This command validates and displays the selection without contacting
Ollama, submitting a prompt, or loading the model.

Submit one prompt to the configured model and print validated answer chunks as
they arrive. In
PowerShell, `--%` preserves the nested quotes through the Gradle batch wrapper:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

The request goes only to the fixed loopback endpoint
`http://127.0.0.1:11434/api/generate`, requests streaming NDJSON, validates each
answer chunk before printing it, and assembles the same chunks into one bounded
final answer. Ordinary requests explicitly disable thinking and use a 512-token
generation default. The prompt is limited to 4,096 characters; the response is
limited to 1 MiB and 65,536 characters; and the complete request is bounded to
five minutes with a 60-second no-data deadline. Ordinary thinking-off requests
show only answer content. Explicit thinking-on requests show `Thinking...`, then
`Answer:`, without displaying raw reasoning. Ctrl+C interrupts the command and
cancels the response subscription. Provider truncation, local byte/text limits,
pre-response unavailability, request rejection, accepted-stream transport loss,
total timeout, inactivity timeout, cancellation, malformed records, and
incomplete streams are distinct outcomes with safe recovery guidance. If one
occurs after visible content, KAOS terminates the
stdout line and identifies the output as partial in safe stderr guidance. KAOS
does not retry after partial output and does not echo prompts
or raw Ollama failures in errors. However,
the quoted prompt can remain in shell history or be visible as a process
argument, so this developer CLI is not an appropriate input surface for
secrets or other private prompts.

The model remains your explicit choice. Current measurements recommend
`qwen3:1.7b` only for a fast connectivity smoke test,
`qwen3:4b-instruct` for ordinary local development, and `qwen3:4b` only for
opt-in reasoning where extra latency and token use are acceptable. These are
developer profiles, not hard-coded defaults or automatic fallbacks. See the
[model scenario benchmark](docs/evolution/ollama-model-scenario-benchmark.md)
for the controlled process, results, and limitations.

Enable reasoning only by deliberately selecting both a supported reasoning
model and thinking mode:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
$env:KAOS_OLLAMA_THINKING = "on"
./gradlew.bat --% run --args="ollama-prompt \"<reasoning prompt>\""
```

KAOS sends `think: true`. When the provider emits separated thinking, KAOS
prints one `Thinking...` progress line and then transitions to an `Answer:`
section while keeping the raw reasoning trace hidden. Unsupported models fail
safely and are not replaced or retried automatically. See the
[thinking policy and benchmark](docs/evolution/ollama-thinking-policy-and-benchmark.md).
Thinking-on requests default to 2,048 generated tokens. Set
`KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT` to a deliberate value from 64 through 4,096
when a request needs a different bounded maximum. See the
[response-generation limit benchmark](docs/evolution/ollama-response-generation-limit-benchmark.md).

Handled startup or application failures return exit code `1` and emit one safe
record such as `ERROR [KAOS-CONFIG-001] ...` on standard error. Expected CLI
usage errors retain exit code `2`. Exception messages, stack traces, arguments,
and configured values are not logged.

The application name can be overridden locally. For the Gradle run workflow,
set the environment variable:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run
```

Direct JVM launches may instead set `-Dkaos.app.name="Local KAOS"`; that system
property takes precedence over `KAOS_APP_NAME`.

The application-name default is `KAOS`. Names are trimmed, limited to 64
Unicode characters, and may contain letters, numbers, spaces, periods,
underscores, or hyphens.

The Ollama model uses `kaos.ollama.model` before `KAOS_OLLAMA_MODEL` and has no
default. Its context uses `kaos.ollama.context-window` before
`KAOS_OLLAMA_CONTEXT_WINDOW`, then the measured 4,096-token ordinary default;
accepted context values are 2,048 through 65,536. A model name is trimmed,
limited to 128 ASCII characters, and supports ordinary or namespaced Ollama
identifiers with an optional tag. Thinking uses `kaos.ollama.thinking` before
`KAOS_OLLAMA_THINKING`, then defaults to `off`; only `off` and `on` are
accepted. No secret, remote endpoint, prompt-file, automatic thinking mode, or
persistent configuration is implemented. Response generation uses
`kaos.ollama.response-token-limit` before
`KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`, then 512 for thinking off or 2,048 for
thinking on; only whole values from 64 through 4,096 are accepted.

## Development rule

Start with the smallest working application. Add packages, Gradle modules,
libraries, repositories, or independently deployed services only when current
implementation evidence shows that they solve a real problem.

Previous issues outside the #814 hierarchy are not development requirements.
Historical source may be inspected as evidence, but reuse decisions must be made
and documented by the active evolutionary roadmap.

The living architecture page is part of the feature definition of done whenever
a feature changes the system structure or its verified architectural status.

## Verify the local application

Run the complete from-clean-state checkpoint:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

This compiles, runs all tests, packages the application, executes deterministic
`status` and `help` smoke commands, and prints a final success checkpoint only
when every prerequisite passes. Use `verifyLocal` without `clean` for an
incremental check.

## Next checkpoint

Epics 000-001 and Features #845 through #848 are complete. Epic #3 continues
with active AI Error and Timeout Handling Feature #849. Task #1074 classifies
the complete local Ollama request lifecycle and presents safe, phase-specific
recovery guidance. AI Integration Testing Feature #850 remains the next
approved checkpoint after Feature #849 is reviewed and merged.
