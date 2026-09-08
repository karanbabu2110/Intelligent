# `read_local_file` Integration and Testing

Feature [007.09](https://github.com/karanbabu2110/KAOS/issues/891) completes the
first-tool outcome through one runnable `read-local-file <question>` command and
deterministic integration evidence. It composes the contracts delivered by the
earlier Epic 007 features; it does not introduce a generic tool runtime.

## Runnable outcome

From PowerShell at the repository root:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_TOOL_READ_ROOT = (Resolve-Path ".").Path
./gradlew.bat --% run --args="read-local-file \"Explain src/main/java/io/kaos/app/KaosApplication.java\""
```

The quoted argument is the user's question, not direct authority or a direct
file path parameter. KAOS sends the question to fixed loopback Ollama with only
the `read_local_file` definition. Ollama may answer without a file or request
one supported relative path. A requested path follows this single lifecycle:

1. load the explicit read root and validate the target without reading content;
2. display the exact resolved path, byte count, and disclosure consequence;
3. accept exactly `approve` or `deny` for this target and invocation;
4. after approval, claim the single-use grant, revalidate, and read once;
5. append the assistant tool request and structured bounded tool result to a
   second Ollama chat request;
6. permit only a final answer, print it, and print one content-free audit line.

The continuation request deliberately omits `tools`. A further tool call is an
invalid provider response. This is the minimum single-shot continuation shape
documented by [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling).

## Observable outcomes

- A direct model answer prints without reading a file or asking for approval.
- Approval followed by a clean read prints the final model answer and an audit
  outcome of `decision=APPROVED outcome=SUCCEEDED`.
- `deny`, invalid approval input, end-of-input, or pre-read cancellation prints
  a no-read result and `outcome=NOT_EXECUTED`; these are normal user-control
  outcomes, not tool failures.
- Permission and execution failures use the fixed `KAOS-TOOL-READ-*`
  diagnostics and never include paths, content, exception messages, causes, or
  stack traces.
- A provider failure after a successful read reports the appropriate existing
  `KAOS-AI-*` diagnostic. Its audit still says the local read succeeded; KAOS
  does not persist the content, retry the read, or retry the provider request.

The audit line includes the operation, a random per-invocation target identity,
decision, and outcome. It includes neither the path nor the file content.

## Deterministic verification

Run the integration-focused slice without an installed Ollama model:

```powershell
./gradlew.bat test --tests 'io.kaos.tool.*' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --tests 'io.kaos.app.ReadLocalFileCommandIntegrationTest' --tests 'io.kaos.app.CommandRouterTest' --no-daemon --rerun-tasks
```

The tests use temporary local files, in-process application collaborators, and
a loopback HTTP fixture. They prove:

- exact tool advertisement and strict tool-call decoding;
- assistant tool-call plus tool-result continuation encoding;
- omission of tools from the continuation request;
- request/result identity matching before provider submission;
- end-to-end approval, revalidation, execution, answer, and audit behavior;
- denial without execution or continuation;
- privacy-safe missing-target and post-read provider failures; and
- public command routing and usage shape.

The complete repository checkpoint remains:

```powershell
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

## Safety and ownership

The local user owns the file and every approval decision. KAOS owns path and
metadata validation, the one-attempt grant, bounded decoding, result framing,
diagnostics, and the content-free audit value. File text exists only during the
foreground command and is sent only to the already fixed loopback Ollama
boundary. It is never written to memory, conversations, knowledge storage, or
audit storage.

The result is an explicit `tool` protocol message, so file text is data rather
than an application or user instruction. It remains untrusted, and model output
remains untrusted. The command cannot guarantee that a model will interpret
hostile text correctly.

## Deliberate limits

This first integration supports one model-selected relative path, one approval,
one file of at most 2,048 strict UTF-8 bytes, one continuation, and no retry.
It does not support direct path execution, multiple or chained tools,
directories, persistence, remote providers, binary or compound documents,
automatic model installation, or a general registry/dispatcher/plugin design.
