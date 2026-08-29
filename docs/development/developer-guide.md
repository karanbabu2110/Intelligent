# KAOS Developer Guide

This is the practical entry point for developing the current KAOS application.
It describes the application that exists now; planned capabilities do not add
setup or operational requirements until they are implemented.

## Prerequisites

- Git
- A Java 21 JDK available to the Gradle toolchain
- PowerShell or Command Prompt on Windows, or a POSIX-compatible shell on
  Linux/macOS
- Network access the first time Gradle needs to download declared build or test
  dependencies
- Optional: a local Ollama server on `127.0.0.1:11434` to demonstrate
  `ollama-status`, plus one installed model to demonstrate `ollama-prompt`;
  neither is required to build or run automated tests

No system Gradle installation is required. Use the Gradle wrapper committed to
the repository.

Check the active Java runtime:

```powershell
java -version
```

## Get the repository

```powershell
git clone https://github.com/Knowledge-Autonomous-Operating-System/KAOS.git
cd KAOS
```

All commands in this guide run from the repository root.

## Verify a new checkout

Windows PowerShell or Command Prompt:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Linux or macOS:

```bash
./gradlew clean verifyLocal --no-daemon --warning-mode=all
```

This canonical checkpoint compiles production and test source, runs all tests
and checks, creates the application artifacts, runs deterministic `status` and
`help` smoke checks, and prints the final success message only when every step
passes.

Use the faster incremental form after a previously verified clean checkout:

```powershell
./gradlew.bat verifyLocal --no-daemon
```

## Run the application

Show the current local status:

```powershell
./gradlew.bat run --args=status
```

Show supported commands:

```powershell
./gradlew.bat run --args=help
```

The no-argument form is equivalent to `status`:

```powershell
./gradlew.bat run
```

The successful status output is:

```text
KAOS application baseline is running.
```

Check the fixed local Ollama endpoint:

```powershell
./gradlew.bat run --args=ollama-status
```

When Ollama is available, the command prints a validated version such as:

```text
Local Ollama is reachable (version 0.32.1).
```

This command performs one bounded `GET /api/version` request to
`http://127.0.0.1:11434`. It does not select a model, submit a prompt, stream a
response, read credentials, or support a remote endpoint. `status` and `help`
do not create the Ollama client or make a network request.

Configure and inspect the model reserved for later AI commands:

Install the ordinary development recommendation explicitly if it is not
already present:

```powershell
ollama pull qwen3:4b-instruct
```

KAOS never runs this installation command for you.

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat run --args=ollama-model
```

Successful output is:

```text
Configured local Ollama model: qwen3:4b-instruct (context window: 4096 tokens, thinking: off, response limit: 512 tokens).
```

`ollama-model` validates and displays local process configuration only. It does
not contact Ollama, check whether the model is installed, download or load a
model, submit a prompt, or produce a response.

Submit one prompt and wait for one complete response. PowerShell needs its
stop-parsing token so nested quotes survive the Gradle batch wrapper:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_THINKING = "off"
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "512"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

On Linux or macOS:

```bash
export KAOS_OLLAMA_MODEL=qwen3:4b-instruct
export KAOS_OLLAMA_THINKING=off
export KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT=512
./gradlew run --args='ollama-prompt "Why is the sky blue?"'
```

The command validates a single 4,096-character prompt, loads the explicit
model selection, and sends `POST /api/generate` to the fixed loopback Ollama
endpoint with `stream` set to `true`. It parses newline-delimited JSON as it
arrives, validates each answer chunk, prints and flushes it once, and assembles
the same chunks into the bounded final answer. It also sends `think: false` and
`options.num_predict: 512` for this ordinary configuration. The response body
is bounded to 1 MiB, generated answer and hidden thinking text to 65,536 Unicode
code points each, inactivity to 60 seconds, and the complete request to five
minutes. The HTTP publisher supplies one bounded item at a time. Ordinary
thinking-off requests retain the unlabeled answer stream. Explicit thinking-on
requests show a content-free progress line and an answer heading without
displaying raw reasoning. There is no retry after visible output, conversation,
system-prompt, tool, image, remote-provider, or persistence behavior.

Clean `done_reason: stop` completion returns exit `0`. Provider
`done_reason: length`, local byte/text ceilings, inactivity or total timeout,
thread interruption, malformed/incomplete streams, and transport failure return
exit `1` through distinct internal outcomes. When content was already visible,
KAOS finishes the stdout line before printing a coded stderr error beginning
`Partial streaming output was displayed before clean completion.` It never
includes the prompt, raw response record, reasoning, or exception detail.

The CLI argument can remain in shell history and may be visible to local
process inspection. Do not use this developer command for secrets or other
private prompts. KAOS does not echo the prompt, raw provider body, configured
model, exception, or stack trace when the request fails.

An unknown command or extra argument returns usage exit code `2`. A handled
configuration or application failure returns exit code `1` and a safe coded
error on standard error. Supplied values, exception messages, and stack traces
are not logged.

## Local configuration

Override the display name for an ordinary local run:

```powershell
$env:KAOS_APP_NAME = "Local KAOS"
./gradlew.bat run --args=status
```

Direct JVM launches may use the `kaos.app.name` system property. Configuration
precedence is:

1. `-Dkaos.app.name=...`
2. `KAOS_APP_NAME`
3. the safe default `KAOS`

Names are trimmed, limited to 64 Unicode characters, and may contain letters,
numbers, spaces, periods, underscores, or hyphens.

The local Ollama model and its bounded context are configured separately:

1. `-Dkaos.ollama.model=...` for a direct JVM launch
2. `KAOS_OLLAMA_MODEL`
3. no default; an explicit selection is required by `ollama-model` and
   `ollama-prompt`

The context-window precedence is:

1. `-Dkaos.ollama.context-window=...` for a direct JVM launch
2. `KAOS_OLLAMA_CONTEXT_WINDOW`
3. 4,096 tokens

KAOS accepts whole values from 2,048 through 65,536 and sends the selection as
Ollama `options.num_ctx`. Use 2,048 only for deliberately short smoke tests.
The [controlled context benchmark](../evolution/ollama-context-window-benchmark.md)
shows why 4,096 is the ordinary default and why larger values remain opt-in.

The thinking-mode precedence is:

1. `-Dkaos.ollama.thinking=off|on` for a direct JVM launch
2. `KAOS_OLLAMA_THINKING=off|on`
3. `off`

Ordinary requests should use `qwen3:4b-instruct` with `off`. To opt into
reasoning deliberately:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
$env:KAOS_OLLAMA_THINKING = "on"
./gradlew.bat run --args=ollama-model
./gradlew.bat --% run --args="ollama-prompt \"<reasoning prompt>\""
```

KAOS sends the boolean mode explicitly. When thinking is on and the provider
emits thinking records, KAOS prints `Thinking...` once, then prints `Answer:`
before streaming final-answer chunks. Raw reasoning remains separately bounded
and is never printed, logged, or included in errors. If the provider emits no
thinking, KAOS does not claim that thinking occurred and begins with `Answer:`.
It does not infer a mode from the prompt, retry with thinking off, or substitute
a model. An unsupported model therefore fails through the safe prompt-rejection
path.

The response-token-limit precedence is:

1. `-Dkaos.ollama.response-token-limit=...` for a direct JVM launch
2. `KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`
3. 512 tokens when thinking is `off`, or 2,048 when thinking is `on`

KAOS accepts whole values from 64 through 4,096 and sends the selection as
Ollama `options.num_predict`. For example, request more bounded space for a
larger ordinary answer:

```powershell
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "1024"
./gradlew.bat --% run --args="ollama-prompt \"<larger prompt>\""
```

KAOS does not use Ollama's unbounded default, retry automatically, or increase
the limit after truncation. The
[response-generation limit benchmark](../evolution/ollama-response-generation-limit-benchmark.md)
records the comparison and explains the separate defaults.

PowerShell environment values persist for the current terminal session. After
running a low-limit boundary test, inspect the effective KAOS configuration
before treating `KAOS-AI-003` as evidence that a default is too small:

```powershell
Get-ChildItem Env:KAOS_OLLAMA_*
./gradlew.bat run --args=ollama-model
```

Return specifically to the mode-based response default by removing only the
response-limit override, then inspect the configuration again:

```powershell
Remove-Item Env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT -ErrorAction SilentlyContinue
./gradlew.bat run --args=ollama-model
```

This does not remove the selected model, context window, or thinking mode.
When thinking is `on`, both hidden reasoning and the final answer consume the
same generated-token allowance, so ordinary questions should normally use the
instruct model with thinking `off`.

Model names are trimmed, limited to 128 ASCII characters, and accept ordinary
or slash-separated identifiers containing letters, numbers, periods,
underscores, or hyphens, followed by an optional colon tag. Examples include
`llama3.2:latest` and `hf.co/team/model-name:Q4_K_M`.

Use the measured profile that matches the current goal:

| Goal | Explicit model | Guidance |
| --- | --- | --- |
| Connectivity smoke test | `qwen3:1.7b` | Fastest and smallest; do not treat its answer as the quality baseline |
| Ordinary local development | `qwen3:4b-instruct` | Current recommendation for answers, summaries, and simple Java help |
| Explicit reasoning experiment | `qwen3:4b` | Opt-in only; the measured reasoning probe needed 674 generated tokens and about 18 seconds |

These names are recommendations, not KAOS defaults. Select and install models
deliberately; a missing model is not downloaded or replaced automatically. The
[model scenario benchmark](../evolution/ollama-model-scenario-benchmark.md)
records the prompts, controls, quality observations, performance, hardware,
licenses, and decision limits. Thinking behavior is recorded in the
[thinking policy and benchmark](../evolution/ollama-thinking-policy-and-benchmark.md).
Response-generation limits are recorded in the
[response-generation limit benchmark](../evolution/ollama-response-generation-limit-benchmark.md).

Do not commit credentials or other secrets. The Ollama endpoint remains fixed
to loopback. No secret, remote endpoint, prompt-file, or persistent
configuration is currently implemented.

## Run tests

Run every test:

```powershell
./gradlew.bat test --no-daemon
```

Run the current application package tests for focused feedback:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon
```

Run the Ollama connectivity and application integration tests together:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.*' --tests 'io.kaos.app.*' --no-daemon
```

Run only the real child-process and timeout scenarios:

```powershell
./gradlew.bat test --tests 'io.kaos.app.KaosApplicationProcessTest' --no-daemon
```

Force a focused suite to execute again even when Gradle considers it up to date:

```powershell
./gradlew.bat test --tests 'io.kaos.app.*' --no-daemon --rerun-tasks
```

Use `verifyLocal`, not only `test`, before completing an application feature.
It additionally proves compilation, checks, packaging, and the real `status`
and `help` application entry points.

## Useful Gradle commands

| Goal | Windows command |
| --- | --- |
| Compile production source | `./gradlew.bat classes` |
| Run all tests | `./gradlew.bat test` |
| Run checks and create artifacts | `./gradlew.bat build` |
| List verification tasks | `./gradlew.bat tasks --group verification --no-daemon` |
| Inspect full verification order | `./gradlew.bat clean verifyLocal --dry-run --no-daemon` |
| Clean and fully verify | `./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all` |

For Linux/macOS, replace `./gradlew.bat` with `./gradlew`.

## Generated outputs

Gradle creates reproducible output under `build/`, including:

- compiled production and test classes;
- the test report at `build/reports/tests/test/index.html`;
- JAR and distribution artifacts under `build/libs/` and
  `build/distributions/`;
- generated start scripts under `build/scripts/`.

`./gradlew.bat clean` removes the Gradle build output so it can be regenerated.
It does not remove application data because the current application creates no
product state.

## Development workflow

1. Select one active feature under
   [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814).
2. Create the feature branch. Keep all of that feature's stories or direct tasks
   on the same branch.
3. Implement the smallest useful behavior and run focused tests during the
   feedback loop.
4. Update documentation that the behavior actually changes.
5. Update the [living architecture website](../../ui/architecture/index.html)
   when packages,
   dependencies, integrations, data ownership, runtime flows, deployment, or
   architectural status change. Leave it unchanged when the architecture did
   not change.
6. Run the complete clean `verifyLocal` checkpoint.
7. Commit each story or task with its roadmap identifier and issue number, for
   example `feat(STORY-002.01.01): connect to local Ollama #<issue>`.
8. Open one pull request for the completed feature.

Do not create a Git tag or GitHub release unless the user explicitly requests
one.

## Troubleshooting

### Java or the toolchain is unavailable

Run `java -version` and confirm that a Java 21 JDK is installed and available.
Correct `JAVA_HOME` or the shell path when they reference an unavailable or
incompatible runtime, then rerun the wrapper command.

### Gradle cannot resolve dependencies

The first build may require network access for declared build and test
dependencies. Check the reported repository, proxy, TLS, or cache error and
retry after correcting that environment problem. `status`, `help`, and
`ollama-model` make no provider request; `ollama-status` and `ollama-prompt`
make only their documented loopback requests.

### A test or verification task fails

Use the first failing Gradle task and its report rather than the missing final
success message. Correct the source, test, configuration, toolchain, cache, or
dependency problem, then rerun the focused command. Finish with the clean
`verifyLocal` checkpoint.

### Local configuration changes the run output

Inspect `KAOS_APP_NAME`, `KAOS_OLLAMA_MODEL`, `KAOS_OLLAMA_CONTEXT_WINDOW`,
`KAOS_OLLAMA_THINKING`, `KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`, and
the corresponding `kaos.app.name`, `kaos.ollama.model`,
`kaos.ollama.context-window`, `kaos.ollama.thinking`, or
`kaos.ollama.response-token-limit` system property. The
`verifyLocal` smoke
tasks deliberately supply the safe `KAOS` application name and do not invoke
`ollama-model`, so they remain deterministic without a model selection.

### Ollama model configuration is rejected

Set `KAOS_OLLAMA_MODEL` to one installed model name you intentionally chose,
set `KAOS_OLLAMA_THINKING` to `off` or `on`, and set any explicit response-token
limit to a whole value from 64 through 4,096, then rerun `ollama-model`. Remove
spaces, control characters, empty namespace segments, or unsupported model
punctuation. KAOS does not echo invalid configured values in its error message.
`ollama-prompt` is the first command that asks Ollama to use the configured
model and thinking setting, so an unavailable model or unsupported thinking
request is reported only when that request is submitted.

### An Ollama answer reaches a length boundary

`KAOS-AI-003` means Ollama returned `done_reason: length`. KAOS intentionally
does not print the partial answer. Review both the response-token limit and
context window, then retry with a larger bounded value only when the request
needs it. A larger response limit does not create additional context capacity.

### Ollama is unavailable

Start Ollama locally and verify that its version endpoint responds at
`http://127.0.0.1:11434/api/version`, then rerun `ollama-status`. Feature 002.01
does not support changing the endpoint, retrying automatically, or connecting
to a remote host. An invalid response should be treated as an Ollama
installation/version problem rather than printed as raw provider data.

### An Ollama prompt fails or times out

First run `ollama-status`, then use `ollama list` to confirm that the configured
model is installed. A rejected request returns `KAOS-AI-002` without the raw
Ollama body. Validated answer content is displayed progressively, but model
loading can still delay the first chunk. KAOS allows at most 60 seconds without
provider data and five minutes overall, then cancels the subscription and
suggests retrying or choosing a faster installed model. Malformed, incomplete,
or locally oversized streams fail safely. If output was visible, treat it as
partial and do not retry automatically; decide whether a new prompt is safe.
Broader reusable error and timeout taxonomy remains Feature #849.

### Stop a running Gradle command

Use the shell's normal interrupt, typically Ctrl+C. While the command is active,
KAOS translates process shutdown into command-thread interruption, cancels the
HTTP response subscription, stops further terminal output, and waits up to two
seconds for resource cleanup. It does not retry or resume the partial response.
The current workflow has no background application worker or product state
requiring rollback. KAOS does not start or own the local Ollama process.

## Detailed references

- [Local run and verification workflow](../evolution/local-run-and-verification-workflow.md)
- [Application test harness](../evolution/application-test-harness.md)
- [Basic command-line interaction](../evolution/basic-command-line-interaction.md)
- [Application configuration](../evolution/application-configuration.md)
- [Package-first application structure](../evolution/package-first-application-structure.md)
- [Capability boundary evolution](../evolution/capability-boundary-evolution.md)
- [Completed work and verified evidence](../evolution/completed-work-and-evidence.md)
- [Architecture website structure and maintenance](../../ui/architecture/README.md)
- [Ollama connectivity](../evolution/ollama-connectivity.md)
- [Ollama model configuration](../evolution/ollama-model-configuration.md)
- [Ollama context-window benchmark](../evolution/ollama-context-window-benchmark.md)
- [Ollama model scenario benchmark](../evolution/ollama-model-scenario-benchmark.md)
- [Ollama thinking policy and benchmark](../evolution/ollama-thinking-policy-and-benchmark.md)
- [Ollama response-generation limit benchmark](../evolution/ollama-response-generation-limit-benchmark.md)
- [Ollama prompt submission](../evolution/ollama-prompt-submission.md)

The detailed references retain acceptance evidence, internal contracts, and
historical validation. This guide owns the current developer-facing commands
and workflow and must be updated when they change.
