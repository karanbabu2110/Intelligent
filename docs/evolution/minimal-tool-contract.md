# Minimal `read_local_file` Tool Contract

Feature [007.02](https://github.com/karanbabu2110/KAOS/issues/883) turns the
selected first-tool use case into one executable, provider-visible contract. It
does not yet submit that contract to Ollama or access a file.

## Implemented outcome

The `io.kaos.tool` package now owns exactly one tool definition, request type,
and result type:

```text
read_local_file
  request: {"path":"src/main/java/io/kaos/app/KaosApplication.java"}
  result:  {"path":"...","content":"...","utf8_bytes":123}
```

The request contains exactly one textual `path`; additional, missing, null, or
non-text fields are rejected. The result associates the validated request with
one complete text value and its exact UTF-8 byte count. Generated string
representations omit both path and content to reduce accidental diagnostic
disclosure.

The function definition follows Ollama's documented function-tool shape: a
`type: function` entry containing a name, description, and JSON Schema
parameters object. The application does not yet add it to an Ollama request.

## Request boundary

`ReadLocalFileRequest` admits at most 512 Unicode code points and preserves the
accepted path exactly. It rejects:

- null, blank, or control-containing text;
- absolute, rooted, drive-qualified, or alternate-data-stream-shaped paths;
- `.` and `..` segments, empty segments, and trailing separators;
- unsupported or missing filename extensions; and
- paths rejected by the local Java path implementation.

The supported extension set is the one selected by Feature 007.01: `.txt`,
`.md`, `.log`, `.java`, `.kt`, `.kts`, `.gradle`, `.json`, `.xml`, `.yaml`,
`.yml`, `.properties`, and `.csv`. An accepted request is structurally eligible;
it does not prove that a configured root exists, that the target is contained
by it, or that a file is safe to read. Those executable permission checks remain
Feature 007.05 work.

## Result boundary

`ReadLocalFileResult` admits one complete, non-blank Java string whose strict
UTF-8 representation is no more than **2,048 bytes**. It permits ordinary tab
and line-ending characters and rejects other ISO controls, malformed surrogate
input, partial content, and oversized content.

The 2,048-byte ceiling is intentionally conservative relative to the ordinary
4,096-token model context and is independent of the 1 MiB knowledge-ingestion
limit. UTF-8 bytes are not model tokens, so Feature 007.03 must still account for
the complete user message, history, tool schema, model call, result framing,
and response allowance when constructing real requests. No automatic
truncation is permitted.

## Ownership and lifecycle

- `ReadLocalFileToolContract` owns the fixed provider definition and exact JSON
  request/result shapes.
- `ReadLocalFileRequest` owns model-argument shape and structural path
  validation.
- `ReadLocalFileResult` owns complete text and UTF-8 result bounds.
- All values are immutable foreground-request data.
- No value is persisted, cached, logged, or submitted to a provider by this
  feature.

The result encoder intentionally contains private path and content because its
sole future consumer is the continuing local model request. Callers must not
use that payload as a diagnostic or audit representation.

## Failure and safety behavior

Contract violations throw bounded `IllegalArgumentException` messages that do
not include argument values or file content. Null object dependencies use
`NullPointerException` consistently with existing immutable value types. There
is no retry, fallback, file read, state transition, or partial result.

This remains a Guarded capability precursor: the data types can represent
private user content, but Feature 007.02 itself performs no filesystem or model
operation. Approval cannot be implemented meaningfully until a validated model
tool request exists, so Feature 007.04 retains that responsibility.

## Verification

Focused tests prove:

- exact function definition and one-field decoding;
- every selected extension and exact path preservation;
- absolute, escaping, ambiguous, oversized, unsafe, and unsupported paths are
  rejected;
- ASCII and multibyte UTF-8 values obey the exact byte ceiling;
- unsafe, malformed, blank, partial, and oversized result content is rejected;
- encoded results preserve exact private values for their intended consumer;
  and
- ordinary string representations do not disclose path or content.

## Deliberate limits and handoff

This feature adds no tool interface, registry, dispatcher, plugin, module,
service, generic schema abstraction, command, approval prompt, read-root
configuration, filesystem access, Ollama request change, execution result
continuation, audit persistence, or runtime capability claim.

The next ordered feature is
[007.03 - Tool Invocation](https://github.com/karanbabu2110/KAOS/issues/886).
It should submit this single definition through the existing local Ollama chat
boundary and decode at most one requested `read_local_file` call without
executing it.

## Reference

- [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling)
