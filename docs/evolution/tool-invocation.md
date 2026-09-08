# Bounded `read_local_file` Invocation

Feature [007.03](https://github.com/karanbabu2110/KAOS/issues/886) connects the
fixed `read_local_file` contract to one explicit operation on the existing
loopback Ollama chat client. It recognizes a pending request but does not read a
file or expose the operation through an application command.

## Implemented outcome

`OllamaPromptClient.submitWithReadLocalFileTool` sends one ordinary bounded
prompt with exactly the function definition from `ReadLocalFileToolContract`.
The validated terminal result contains exactly one of:

- a non-blank ordinary model answer; or
- one structurally valid `ReadLocalFileRequest` awaiting later approval.

Existing `submit` operations do not include a `tools` property. Consequently,
the `ollama-prompt`, conversation, and grounded-answer paths remain unchanged
and reject an unadvertised provider tool call as invalid.

The request uses the existing fixed loopback `POST /api/chat` endpoint,
streaming response framing, model configuration, byte ceilings, total deadline,
inactivity deadline, interruption behavior, completion metrics, and
privacy-safe status taxonomy. It adds no provider, endpoint, retry, dependency,
or transport abstraction.

## Provider exchange

The enabled request adds one entry to `tools`:

```json
{
  "type": "function",
  "function": {
    "name": "read_local_file",
    "description": "Read one approved UTF-8 text-based file below the configured local read root.",
    "parameters": {
      "type": "object",
      "properties": {
        "path": {
          "type": "string",
          "description": "One normalized relative path below the local read root.",
          "maxLength": 512
        }
      },
      "required": ["path"],
      "additionalProperties": false
    }
  }
}
```

A provider call is accepted only when `message.tool_calls` is an array
containing one function named exactly `read_local_file`, and its arguments pass
the existing one-field decoder. The client accumulates no more than one call
across the complete stream.

The operation accepts an ordinary answer when the model decides no file is
needed. Answer text and a tool request are mutually exclusive. Unknown,
unadvertised, malformed, mixed, or multiple calls make the whole result
invalid. A `done_reason: length` result discards a pending call because its
arguments cannot be treated as cleanly complete.

## Authority and lifecycle

The model's function call is a **request**, not permission and not proof that a
file exists. A successful pending result:

- has crossed only the existing loopback model boundary;
- contains the structurally validated relative path;
- has not resolved the configured read root;
- has not inspected metadata or opened a file;
- has not printed or persisted the path; and
- grants no present or future execution authority.

The request is immutable foreground data. Its ordinary string representation
redacts the path, and the enclosing client result reports only status and
whether a tool was requested. The explicit accessor exists for the future
approval coordinator; callers must not use it as a general diagnostic.

## Streaming and failure rules

- Thinking remains subject to the configured thinking policy and is never used
  as a tool request.
- A tool call must be a complete JSON arguments object in one streamed record;
  fragmented argument reconstruction is not implemented.
- At most one tool call may appear in one record or across the stream.
- Any answer content combined with a tool call is rejected, including
  whitespace-only content.
- The terminal record must retain the existing supported completion reason and
  non-negative metrics.
- Timeout, interruption, byte-limit, malformed UTF-8, request rejection, and
  transport failures return the existing content-free failure result with no
  retained tool request.
- There is no automatic retry or execution after any outcome.

Recovery is to correct the prompt, model, or provider and submit a new request.
Every retry starts a new model lifecycle; it does not inherit authority from a
discarded request.

## Verification

The loopback client tests prove:

- the enabled request advertises exactly the approved function schema;
- ordinary submit paths omit tools and reject unadvertised calls;
- one valid call returns the exact validated relative path without file access;
- the enabled operation may still return an ordinary answer;
- unknown names, malformed arguments, extra arguments, multiple calls, calls
  across multiple stream records, and mixed content fail closed;
- length completion discards the pending request; and
- safe result rendering does not disclose the requested path.

Tests use only an ephemeral loopback HTTP server. They do not contact an
installed Ollama instance and do not create or read a local tool target.

## Deliberate limits and handoff

There is no application command, interactive approval, configured read root,
filesystem validation, file read, result continuation, audit record, generic
tool interface, registry, dispatcher, plugin, module, service, or tool loop.
Model support and selection remain explicit operator responsibilities; KAOS
does not discover or substitute a tool-capable model.

Implementation evidence changed the next safe checkpoint. Approval must name
the exact resolved target, so
[007.05 - Input and Permission Validation](https://github.com/karanbabu2110/KAOS/issues/888)
must establish that target before
[007.04 - User Approval](https://github.com/karanbabu2110/KAOS/issues/885)
can request meaningful authority. This is a dependency-order correction, not a
scope change to either feature.

## Reference

- [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling)
