# `read_local_file` User Approval

Feature [007.04](https://github.com/karanbabu2110/KAOS/issues/885) adds the
explicit local-user decision that must occur after metadata-only target
validation and before any file content can be read.

## Implemented outcome

`ReadLocalFileApprovalRequest` binds one approval prompt to one previously
validated `ReadLocalFileTarget`. Its intentionally path-revealing `prompt()`
text shows only the information needed for an informed decision:

- the exact tool name, `read_local_file`;
- the exact absolute, normalized, real target path;
- the validated byte count;
- that the file text will be supplied to the configured local Ollama model for
  the current answer only; and
- the only recognized response tokens, `approve` and `deny`.

The prompt must be shown only to the local user. Unlike diagnostic string
representations, it deliberately contains the approved target path. It does
not contain file content or unrelated paths.

## Decision and lifecycle

`decide` strips surrounding whitespace and then applies an exact,
case-sensitive token comparison:

| Input | Outcome | Grant |
| --- | --- | --- |
| `approve` | `APPROVED` | One single-claim grant |
| `deny` | `DENIED` | None |
| Explicit `cancel()` | `CANCELLED` | None |
| End of input (`null`) | `END_OF_INPUT` | None |
| Any other value | `INVALID_RESPONSE` | None |

Each approval request can be decided only once. An invalid response consumes
the request and fails closed rather than allowing a later permissive answer.
The later command boundary can map interruption to `cancel()`, making
cancellation an observable no-grant outcome. A retry requires a new model tool
request, validated target, and approval request.

An approved outcome owns one `ReadLocalFileApprovalGrant`. The grant returns
the exact validated target through `claimTarget()` once and rejects every later
claim. It grants one execution attempt, not successful execution, because the
next feature must still revalidate and safely read the target. It does not
grant access to a directory, extension, session, or future request.

## Privacy and safety

No approval production class opens or decodes the target. Prompt rendering
uses only metadata already present in `ReadLocalFileTarget`. The approval
request, outcome, and grant `toString()` values redact the path; ordinary logs
and diagnostics therefore do not reveal it accidentally.

Approval is not proof that the target remains unchanged. The grant preserves
the original validated target rather than resolving a replacement. The
Feature 007.06 executor claims the grant once, revalidates that target, uses
no-follow bounded file opening, validates strict UTF-8, and fails closed before
returning any tool result.

## Verification

Focused tests use temporary local files and prove:

- the prompt contains the exact target, size, disclosure, and response tokens
  but not file content;
- only the exact lowercase `approve` token creates a grant;
- denial, explicit cancellation, end of input, empty, case-changed, compound,
  and multiline responses create no grant;
- one request cannot be decided twice and one grant cannot be claimed twice;
  and
- diagnostic strings do not expose the absolute target path.

No test or approval production class sends data to Ollama, reads target
content, persists state, or contacts an external service.

## Deliberate limits and handoff

This feature adds no application command, terminal input loop, file execution,
UTF-8 decoding, model-result continuation, audit persistence, reusable
permission store, generic approval framework, module, service, plugin, or new
dependency. Mapping process interruption to `cancel()` remains the
responsibility of the later command boundary.

The successor
[007.06 - Tool Execution Result](https://github.com/karanbabu2110/KAOS/issues/887)
claims one approval grant, revalidates and reads the same target once, and
constructs one bounded `ReadLocalFileResult` without broadening the tool
surface.
