# `read_local_file` Execution Result

Feature [007.06](https://github.com/karanbabu2110/KAOS/issues/887) consumes one
explicit approval grant and performs one bounded read of the exact validated
local-file target. It returns either one complete `ReadLocalFileResult` or a
content-free failure; it never returns partial file content.

## Implemented outcome

`ReadLocalFileExecutor` accepts only a `ReadLocalFileApprovalGrant`, so an
ordinary request or validated target is not sufficient authority. Execution
uses this sequence:

1. claim the grant, permanently consuming its one attempt;
2. stop if the executing thread is already interrupted;
3. revalidate the approved target and its metadata fingerprint;
4. open the exact real path for reading with `NOFOLLOW_LINKS`;
5. read at most 2,048 bytes plus one overflow probe;
6. stop on interruption without returning buffered content;
7. revalidate the same target again and require the read length to match;
8. decode the complete bytes with a reporting UTF-8 decoder; and
9. construct the existing bounded `ReadLocalFileResult`.

The grant is consumed before validation or I/O. A failed attempt therefore
cannot be retried with the same approval. A retry requires a new model tool
request, validation, and explicit approval.

## Content contract

The result preserves the structurally validated relative request and the exact
decoded text. The existing result contract rejects blank text, unsupported
control characters, malformed Unicode, and content above 2,048 UTF-8 bytes.
Line feeds, carriage returns, and tabs remain supported. No decoding fallback,
replacement character, truncation, pagination, or automatic retry exists.

## Failure contract

Pre-read and post-read target failures retain the content-free
`ReadLocalFilePermissionException` categories from Feature 007.05. Failures
owned by execution use `ReadLocalFileExecutionException`:

| Reason | Meaning |
| --- | --- |
| `UNAVAILABLE` | The approved file could not be opened or read safely |
| `CHANGED` | The opened stream exceeded the approved bound or its length no longer matched |
| `INVALID_UTF8` | The complete bytes were not strict UTF-8 |
| `INVALID_CONTENT` | Decoded text was blank or contained unsupported content |
| `CANCELLED` | The executing thread was interrupted before completion |

The public execution message and executor string representation contain no
path, file content, filesystem exception, or stack-trace detail. Expected I/O
causes are deliberately not retained because their messages can disclose
private paths.

## Race boundary

No-follow opening plus validation before and after reading detects ordinary
replacement, link, size, timestamp, and file-key changes. The executor also
requires the actual byte count to match the validated target.

This remains a best-effort metadata boundary, not a filesystem transaction or
cryptographic identity guarantee. A filesystem or adversary able to change
content while preserving every available metadata field may evade detection.
The executor limits exposure by reading only one small complete value and
returning immutable text after the post-read check. Stronger operating-system
handle identity is deferred until current evidence demonstrates a supported,
portable need.

## Verification

Focused temporary-filesystem tests prove:

- exact multilingual UTF-8 and exact-ceiling results;
- the source file remains unchanged;
- malformed UTF-8, blank text, and unsupported control characters fail without
  a result;
- changed and oversized targets fail before reading;
- interruption consumes the grant and returns `CANCELLED`;
- every execution attempt consumes its grant; and
- failure messages and diagnostic strings expose neither target nor content.

The tests do not contact Ollama, use operator files, persist content, or depend
on an external service.

## Deliberate limits and handoff

This feature adds no application command, interactive input loop, tool-result
submission to Ollama, final model continuation, retry,
directory access, watcher, generic executor framework, module, service,
plugin, native filesystem dependency, or new library.

The successor
[007.07 - Tool Audit Record](https://github.com/karanbabu2110/KAOS/issues/890)
correlates the decision and broad execution outcome through one random,
content-free target identity. It does not persist file or audit data.
