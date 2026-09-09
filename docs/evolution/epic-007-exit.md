# Epic 007 Exit — First Tool Integration

## Outcome

Epic [007 — First Tool Integration](https://github.com/karanbabu2110/KAOS/issues/15)
delivers one bounded action after explicit user approval: the local model can
request one supported file, KAOS can validate and read it once, and the model
can answer the user's current question from that result.

## Completed capability chain

| Feature | Verified contribution | Evidence |
| --- | --- | --- |
| 007.01 | Selected one user-owned local text-file use case | [Use-case definition](tool-use-case-definition.md) |
| 007.02 | Fixed the exact request, result, extensions, and 2,048-byte ceiling | [Minimal contract](minimal-tool-contract.md) |
| 007.03 | Advertised and decoded at most one Ollama tool request | [Invocation](tool-invocation.md) |
| 007.05 | Validated one metadata-only target below an explicit root | [Permission validation](tool-input-permission-validation.md) |
| 007.04 | Bound one explicit approval decision to that exact target | [User approval](tool-user-approval.md) |
| 007.06 | Revalidated and read one strict UTF-8 file with a single-use grant | [Execution result](tool-execution-result.md) |
| 007.07 | Recorded one path-free, content-free final audit value | [Audit record](tool-audit-record.md) |
| 007.08 | Mapped known failures to stable privacy-safe diagnostics | [Failure handling](tool-failure-handling.md) |
| 007.09 | Composed the runnable command and deterministic integration suite | [Integration testing](tool-integration-testing.md) |

## Demonstration

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_TOOL_READ_ROOT = (Resolve-Path ".").Path
./gradlew.bat --% run --args="read-local-file \"Explain src/main/java/io/kaos/app/KaosApplication.java\""
```

The question and model request do not authorize access. KAOS displays the exact
resolved target and disclosure consequence, then accepts exactly `approve` or
`deny`. Approval authorizes one read attempt only. The validated result is sent
to fixed-loopback Ollama as a `tool` message for the final answer; the
continuation advertises no tools.

## Exit evidence

- All nine feature issues are closed and their pull requests are merged.
- The final feature tree completed all 11 `verifyLocal` tasks.
- The complete suite ran 380 tests with zero failures and errors and four
  existing/platform skips.
- Deterministic coverage crosses request encoding, loopback HTTP, validation,
  approval, one-attempt execution, continuation, command routing, denial,
  stable failures, and audit output.
- README and developer documentation describe the actual command,
  configuration, environment variables, argument shapes, and supported files.
- The implementation remains one Java application with direct in-process
  composition and one tool-specific subpackage.

## Safety, privacy, and recovery

- The mandatory read root is absolute and cannot be a filesystem root.
- Absolute, escaping, linked, unsupported, empty, unreadable, oversized,
  changing, binary-like, or malformed UTF-8 targets fail closed.
- File content is foreground-only and is not printed as a diagnostic or stored
  in memory, knowledge, conversations, or audit data.
- Denial, invalid approval input, end-of-input, and pre-read cancellation
  produce no read or provider continuation.
- Retrying requires a new model request, validation, and approval.
- Provider failures never expose response bodies and never repeat the read.

## Deliberate limits

Epic 007 does not provide unrestricted filesystem access, direct path
execution, `.doc`, `.docx`, `.pdf`, binary parsing, directories, multiple
files, chained tools, persistence, remote providers, a generic tool registry,
a plugin framework, another Gradle module, or a separate service.

## Release checkpoint

The completed epic is assigned to milestone
[v1.5.0 — First Tool Integration](https://github.com/karanbabu2110/KAOS/milestone/5)
and is prepared for the cumulative `v1.5.0` release. No next epic is activated
by this exit record; it must be analyzed before work begins.
