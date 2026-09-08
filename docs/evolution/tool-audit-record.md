# `read_local_file` Audit Record

Feature [007.07](https://github.com/karanbabu2110/KAOS/issues/890) adds one
content-free final audit value for one foreground `read_local_file` invocation.
It makes the user's decision and the operation outcome observable without
retaining the requested path or file content.

## Implemented outcome

`ReadLocalFileAuditContext.start` accepts one exact validated target, assigns a
random version-4 UUID as its per-invocation target identity, and immediately
discards the target reference. The UUID is not a path hash, filename encoding,
stable file identifier, or cross-invocation identity. Two invocations for the
same target receive different identities.

The context emits at most one immutable `ReadLocalFileAuditRecord` containing:

- the fixed operation name `read_local_file`;
- the random target identity;
- the approval decision; and
- the final broad outcome.

It contains no path, relative request, filename, extension, byte count, file
metadata, file content, model prompt or answer, exception, failure reason,
user identifier, machine identifier, or timestamp.

## Decision and outcome states

| Decision | Allowed outcome | Meaning |
| --- | --- | --- |
| `APPROVED` | `SUCCEEDED` | The approved execution returned a complete result |
| `APPROVED` | `FAILED` | The approved execution failed without retained detail |
| `APPROVED` | `CANCELLED` | Execution was interrupted after approval |
| `DENIED` | `NOT_EXECUTED` | The user explicitly denied the request |
| `CANCELLED` | `NOT_EXECUTED` | Approval was cancelled before authority existed |
| `INVALID_RESPONSE` | `NOT_EXECUTED` | The response did not authorize the request |
| `END_OF_INPUT` | `NOT_EXECUTED` | Input ended without authorization |

The record rejects impossible combinations: a non-approved decision cannot
have an execution outcome, and an approved decision cannot be recorded as
`NOT_EXECUTED`. An invalid attempt to record an approved decision through the
no-execution path leaves the context available for the correct final outcome.
After one record is created, every other final-record attempt fails.

## Privacy decision

A stable hash of an absolute path would permit dictionary attacks and would
correlate the user's file across otherwise independent invocations. The random
target identity supports correlation inside one foreground invocation without
encoding or retaining target data.

Timestamps are also omitted. The current application has no audit destination,
retention policy, clock requirement, query workflow, or multi-operation stream
that needs ordering. Adding time now would increase metadata collection without
a demonstrated user outcome.

The existing scaffolded observability modules are not imported. They carry
framework and configuration dependencies that the single application does not
need for one immutable value.

## Tool package boundary

Current evidence now justifies one package boundary below the tool namespace:
the complete capability has 13 production types spanning its request, result,
provider contract, validation, approval, execution, failures, and audit value.
All of them are owned by `io.kaos.tool.readlocalfile`, with matching tests in
the same package. `io.kaos.tool` remains only the parent namespace.

The package name follows the implemented tool rather than a technical layer.
A later tool can receive a sibling package when it has real code. No generic
tool interface, registry, dispatcher, or common lifecycle is inferred merely
because multiple tools are expected.

## Verification

Focused tests prove:

- identities are random version-4 UUIDs and differ for the same target;
- approved success, failure, and execution cancellation map correctly;
- denial, approval cancellation, invalid response, and end of input are all
  `NOT_EXECUTED`;
- approved decisions cannot enter the no-execution path;
- one context cannot emit contradictory final records; and
- record and context diagnostics contain no path, content, or failure detail.

Tests use temporary local files only. Production audit code performs no file
read, network request, persistence, logging, or external call.

## Deliberate limits and handoff

This feature defines and returns an in-process value; it does not print, log,
serialize, store, query, rotate, export, or transmit audit records. It adds no
sink, repository, schema, database table, clock, logging framework, module,
service, plugin, or dependency. A process crash before the final factory call
therefore leaves no record, matching the current no-persistence lifecycle.

The next roadmap feature is
[007.08 - Tool Failure Handling](https://github.com/karanbabu2110/KAOS/issues/889).
It can map current permission and execution failures to safe caller behavior
without adding private failure detail to this audit record.
