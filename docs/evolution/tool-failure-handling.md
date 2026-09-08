# `read_local_file` Failure Handling

Feature [007.08](https://github.com/karanbabu2110/KAOS/issues/889) maps every
known `read_local_file` request, permission, execution, cancellation, and
consumed-state failure to one stable capability-specific diagnostic. The
`read-local-file` command delivered by Feature 007.09 now prints these fixed
diagnostics at the application boundary.

## Implemented outcome

`ReadLocalFileFailureMapper` accepts only the two typed exception families
already owned by the capability and supplies explicit factories for invalid
requests and consumed state. It returns one `ReadLocalFileFailure` enum value;
each value owns a stable code, fixed recovery message, and application-compatible
`ERROR [code] message` rendering.

The mapper does not accept an arbitrary `Throwable`. Unexpected programming or
JVM failures therefore remain available to the existing application failure
boundary instead of being mislabeled as a safe, recoverable tool failure.

## Diagnostic catalog

| Code | Failure | Safe recovery |
| --- | --- | --- |
| `KAOS-TOOL-READ-001` | Invalid request | Request one supported relative file path |
| `KAOS-TOOL-READ-002` | Invalid root configuration | Configure one absolute non-filesystem-root directory |
| `KAOS-TOOL-READ-003` | Unavailable root or file | Verify local existence and access, then make a new request |
| `KAOS-TOOL-READ-004` | Outside root or linked traversal | Choose one direct file below the configured root |
| `KAOS-TOOL-READ-005` | Invalid target | Choose one non-empty regular supported text file |
| `KAOS-TOOL-READ-006` | Unreadable file | Correct local read permission, then make a new request |
| `KAOS-TOOL-READ-007` | File too large | Choose a file no larger than 2,048 bytes |
| `KAOS-TOOL-READ-008` | Approved target changed | Make and approve a newly validated request |
| `KAOS-TOOL-READ-009` | Invalid UTF-8 | Convert the file to strict UTF-8 or choose another file |
| `KAOS-TOOL-READ-010` | Invalid decoded content | Choose non-blank text without unsupported controls |
| `KAOS-TOOL-READ-011` | Execution cancelled | Make a new request only when ready for another approval |
| `KAOS-TOOL-READ-012` | Consumed or inactive state | Start a new request rather than reusing authority |

Permission and execution `UNAVAILABLE` reasons share `003`; their recovery is
the same. Their `CHANGED` reasons likewise share `008`. No provider body,
filesystem exception, absolute path, configured value, request text, file
content, model prompt, or stack trace enters a failure value.

## Normal non-execution outcomes

Approval denial, cancellation before approval, invalid approval input, and end
of input already produce typed `ReadLocalFileApprovalOutcome` values with no
grant. They remain observable normal outcomes and are not mapped to error
diagnostics. This prevents an explicit user refusal from being reported as an
application failure.

Execution cancellation is different: approval already existed and the
execution attempt was consumed, so it maps to `KAOS-TOOL-READ-011`. Neither
kind of cancellation retries automatically.

## Ownership and retry policy

The `io.kaos.tool.readlocalfile` package owns classification and recovery text.
The future application coordinator will own whether a returned diagnostic is
shown on standard error or encoded for a local provider continuation. The
existing `ErrorReporter` remains unchanged until that concrete consumer exists.

Every diagnostic recommends a deliberate correction or new request. No value
marks a failure as automatically retryable, because a retry would require a new
model request and, when applicable, a new exact-target approval.

## Verification

Focused tests prove:

- all seven permission reasons and all five execution reasons are mapped;
- request and inactive-state failures are explicit;
- every code matches `KAOS-TOOL-READ-NNN` and is unique;
- every message is non-blank and renders in the application error format;
- private paths, filenames, content, exception types, and package names are
  absent; and
- missing typed failures are rejected rather than guessed.

The tests do not access operator files, contact Ollama, print diagnostics, or
persist any state.

## Deliberate limits and handoff

This feature adds no application command, generic cross-tool error taxonomy,
exception-catching framework, logging sink, audit detail, retry, provider
continuation, persistence, module, service, plugin, or dependency.

The next roadmap feature is
[007.09 - Tool Integration Tests](https://github.com/karanbabu2110/KAOS/issues/891).
It should integrate the completed request, validation, approval, execution,
audit, and failure contracts through one deterministic application path.
That integration is documented in
[tool integration testing](tool-integration-testing.md).
