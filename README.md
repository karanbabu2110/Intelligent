# KAOS

KAOS is one evolving Java application, delivering one useful goal at a time.
The development-model reset is complete; capabilities now grow incrementally
inside the verified single application.

## Current development state

- Roadmap: [KAOS Evolutionary Development Roadmap #814](https://github.com/karanbabu2110/KAOS/issues/814)
- Completed epics: [Epic 000 — Development Model Reset](https://github.com/karanbabu2110/KAOS/issues/815) and [Epic 001 — Minimal KAOS Application](https://github.com/karanbabu2110/KAOS/issues/2)
- Active epic: [Epic 002 — First AI Integration](https://github.com/karanbabu2110/KAOS/issues/3)
- Active feature: [Feature 002.03 — Prompt Submission](https://github.com/karanbabu2110/KAOS/issues/847)
- Repository state: one root Gradle/Java 21 application with one production entry point, optional loopback Ollama connectivity, explicit local model selection, one bounded non-streamed prompt flow, one JSON runtime library, and seventy-four focused tests
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

The latest release is **0.0.1**, tagged as
[`v0.0.1`](https://github.com/Knowledge-Autonomous-Operating-System/KAOS/releases/tag/v0.0.1).
Ongoing development uses Gradle version **0.0.2-SNAPSHOT** so unreleased work
cannot be confused with that baseline.

This is an initial-development baseline. It proves the runnable Java
application and development workflow; it is not a production-ready KAOS or AI
capability release. See the [0.0.1 release notes](docs/releases/v0.0.1.md).

## Run the application

```powershell
./gradlew.bat run --args=status
```

The no-argument form remains supported. Both forms print
`KAOS application baseline is running.` and exit successfully. Run
`./gradlew.bat run --args=help` for the exact supported syntax. Unknown
commands or extra arguments produce safe guidance and a nonzero result without
echoing the supplied values.

Start Ollama before running KAOS AI commands. On Windows, launch **Ollama** from
the Start menu; the installed application runs in the background. When using a
manually managed CLI installation instead, run this in a dedicated terminal and
leave it open:

```powershell
ollama serve
```

Use one startup method, not both. If `ollama serve` reports that port `11434`
is already in use, the Windows application may already be serving; verify it
with `ollama-status` below.

Check whether Ollama is reachable on the fixed local endpoint
`http://127.0.0.1:11434/api/version`:

```powershell
./gradlew.bat run --args=ollama-status
```

A successful check prints only the validated Ollama version. The command sends
no prompt, model name, credential, personal data, or file content. If Ollama is
unavailable or its version response is invalid, KAOS returns a safe
`KAOS-AI-001` error with recovery guidance.

List the models already installed locally:

```powershell
ollama ls
```

If the model you intentionally chose is absent, download it explicitly before
using KAOS. This requires network access, disk space, and time:

```powershell
ollama pull qwen3:8b
```

Select and inspect the model that later AI commands will use:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
./gradlew.bat run --args=ollama-model
```

KAOS requires an explicit selection and does not assume or download a default
model. This command validates and displays the selection without contacting
Ollama, submitting a prompt, or loading the model.

Submit one prompt to the configured model and print one complete response. In
PowerShell, `--%` preserves the nested quotes through the Gradle batch wrapper:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

The request goes only to the fixed loopback endpoint
`http://127.0.0.1:11434/api/generate`, uses JSON, and explicitly disables
streaming. The prompt is limited to 4,096 characters; the response is limited
to 1 MiB and 65,536 characters; and the complete request is bounded to five
minutes. KAOS does not echo prompts or raw Ollama failures in errors. However,
the quoted prompt can remain in shell history or be visible as a process
argument, so this developer CLI is not an appropriate input surface for
secrets or other private prompts.

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
default. A model name is trimmed, limited to 128 ASCII characters, and supports
ordinary or namespaced Ollama identifiers with an optional tag. No secret,
remote endpoint, prompt-file, or persistent configuration is implemented.

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

Epics 000-001 and Features #845-#846 are complete. Project 1 is executing #814,
Epic #3, Feature #847, and its direct Tasks #1065-#1066. Complete and merge the
single Feature 002.03 pull request before activating Response Streaming Feature
#848. No tag or release is created unless the user explicitly requests one.
