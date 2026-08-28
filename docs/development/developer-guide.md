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

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
./gradlew.bat run --args=ollama-model
```

Successful output is:

```text
Configured local Ollama model: qwen3:8b (context window: 4096 tokens).
```

`ollama-model` validates and displays local process configuration only. It does
not contact Ollama, check whether the model is installed, download or load a
model, submit a prompt, or produce a response.

Submit one prompt and wait for one complete response. PowerShell needs its
stop-parsing token so nested quotes survive the Gradle batch wrapper:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

On Linux or macOS:

```bash
export KAOS_OLLAMA_MODEL=qwen3:8b
./gradlew run --args='ollama-prompt "Why is the sky blue?"'
```

The command validates a single 4,096-character prompt, loads the explicit
model selection, sends `POST /api/generate` to the fixed loopback Ollama
endpoint with `stream` set to `false`, and prints only the complete generated
text. The response body is bounded to 1 MiB, generated text to 65,536
characters, and the request to five minutes. There is no streaming, retry,
conversation, system-prompt, tool, image, remote-provider, or persistence
behavior.

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

Model names are trimmed, limited to 128 ASCII characters, and accept ordinary
or slash-separated identifiers containing letters, numbers, periods,
underscores, or hyphens, followed by an optional colon tag. Examples include
`llama3.2:latest` and `hf.co/team/model-name:Q4_K_M`.

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

Inspect `KAOS_APP_NAME`, `KAOS_OLLAMA_MODEL`, `KAOS_OLLAMA_CONTEXT_WINDOW`, and
the corresponding `kaos.app.name`, `kaos.ollama.model`, or
`kaos.ollama.context-window` system property. The `verifyLocal` smoke
tasks deliberately supply the safe `KAOS` application name and do not invoke
`ollama-model`, so they remain deterministic without a model selection.

### Ollama model configuration is rejected

Set `KAOS_OLLAMA_MODEL` to one installed model name you intentionally chose,
then rerun `ollama-model`. Remove spaces, control characters, empty namespace
segments, or unsupported punctuation. KAOS does not echo invalid configured
values in its error message. `ollama-prompt` is the first command that asks
Ollama to use the configured model, so an unavailable model is reported only
when that request is submitted.

### Ollama is unavailable

Start Ollama locally and verify that its version endpoint responds at
`http://127.0.0.1:11434/api/version`, then rerun `ollama-status`. Feature 002.01
does not support changing the endpoint, retrying automatically, or connecting
to a remote host. An invalid response should be treated as an Ollama
installation/version problem rather than printed as raw provider data.

### An Ollama prompt fails or times out

First run `ollama-status`, then use `ollama list` to confirm that the configured
model is installed. A rejected request returns `KAOS-AI-002` without the raw
Ollama body. A complete non-streamed generation may take several minutes while
a model loads or generates on CPU; KAOS waits at most five minutes and then
suggests retrying or choosing a faster installed model. Response Streaming
Feature #848 is intentionally deferred and will provide progressive output.

### Stop a running Gradle command

Use the shell's normal interrupt, typically Ctrl+C. The current workflow has no
background application worker, retry loop, or product state requiring rollback.
KAOS does not start or own the local Ollama process.

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
- [Ollama prompt submission](../evolution/ollama-prompt-submission.md)

The detailed references retain acceptance evidence, internal contracts, and
historical validation. This guide owns the current developer-facing commands
and workflow and must be updated when they change.
