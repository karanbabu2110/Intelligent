# Application debug tracing

Enable data-flow tracing in the PowerShell session that launches KAOS:

```powershell
$env:KAOS_DEBUG = "true"
$env:KAOS_DEBUG_FILE = Join-Path $env:TEMP "kaos-debug.jsonl"
$env:KAOS_WEB_SEARCH_SEARXNG_URL = "http://127.0.0.1:8080"
$env:KAOS_OLLAMA_MODEL = "qwen3.5:4b"
./gradlew.bat --% --console=plain run --args="agent \"find the participants of tamil big boss in season 10.\""
```

This applies to every application command, including conversation, knowledge,
and tool commands. Disable it with `$env:KAOS_DEBUG = "false"`. A direct JVM
launch can use `-Dkaos.debug=true`; the JVM property overrides the environment.
Only `true` (case insensitive) enables tracing; other values leave it off.
Use `KAOS_DEBUG`, not Gradle's `--debug` switch.

Each trace line is a JSON object on stderr containing `debug`, `runId`,
`sequence`, `timestamp`, `elapsedMs`, `stage`, and `data`. Standard command
output remains on stdout. `KAOS_DEBUG_FILE` (JVM override: `kaos.debug.file`)
selects a UTF-8 file instead of stderr. Its parent directory must already exist.
Runs append to the file and have separate run IDs; existing contents are not
overwritten. If opening the file fails, tracing falls back to stderr with a
`debug.file_unavailable` event. Disabled mode neither creates nor writes files.
Use the file setting on Windows PowerShell, whose native stderr redirection
can wrap lines and corrupt JSON. To inspect a saved run:

```powershell
Get-Content $env:KAOS_DEBUG_FILE | ForEach-Object { $_ | ConvertFrom-Json } |
    Where-Object stage -in 'agent.evidence_policy', 'agent.plan.proposal', 'agent.result'
```

The application-managed file contains only KAOS trace entries; ordinary errors
remain on stderr. Do not commit debug output. Files are not rotated or deleted
automatically; manage saved logs yourself.

## Follow the transformation

| Stage | What it explains |
| --- | --- |
| `application.start`, `command.route`, `application.end` | Parsed command arguments, dispatch, exit code, run identity |
| `application.error`, `application.failure` | Stable error code or unexpected exception type; no raw exception message |
| `agent.evidence_policy` | Goal, freshness requirement, local evidence requirement, required path |
| `ollama.configuration.inputs`, `ollama.configuration.rejected`, `ollama.chat.configuration`, `ollama.chat.request`, `ollama.chat.start` | Property/environment inputs, exact rejected setting reason, effective model/thinking/context/token settings, serialized messages, generation options, tool definitions, planning schema, request size and timeout limits |
| `ollama.chat.http`, `ollama.chat.progress`, `ollama.chat.stream_complete`, `ollama.chat.result` | HTTP status, bounded record/character progress, terminal token metrics and completion reason, accepted result or failure |
| `ollama.chat.stream_timeout`, `ollama.chat.transport_failure` | Timeout phase, elapsed and idle time, received event/byte counts, remaining total time, and final transport classification |
| `agent.plan.proposal`, `agent.plan.validated`, `agent.plan.rejected` | Raw proposed plan, accepted evidence shape and step count, or validation rejection reason |
| `agent.model_configuration.failure`, `agent.tool_configuration.failure`, `agent.planning.submission_failure`, `agent.synthesis.submission_failure` | Pre-provider configuration or prompt-construction failures and submission exceptions with exact debug-only type/message |
| `agent.step.start`, `tool.selection`, `tool.transition`, `tool.result` | Step order, arguments and allowlist, permission/execution transitions, retained tool content |
| `search.request`, `search.http`, `search.response`, `search.normalized` | Approved query, service endpoint/status, bounded parsed response, normalized titles/URLs/snippets |
| `agent.synthesis.input`, `agent.result` | Actual instruction and evidence sent to synthesis, final status and answer |
| `knowledge.chunks`, `knowledge.stored`, `knowledge.retrieval` | Extracted text chunks, storage identifier/counts, ranked matches and grounded prompt |
| `ollama.embedding.request`, `ollama.embedding.http`, `ollama.embedding.response` | Embedding model/input, HTTP status, bounded vectors before validation |

For a synthesis-only hallucination, look for `NOT_REQUIRED` in the policy,
`STABLE_INTERNAL` in the proposal, an empty synthesis evidence list, and the
absence of a search request. For malformed arguments, inspect the proposal and
`tool.selection` preceding `agent.plan.rejected`. For token exhaustion, inspect
`options.num_predict`, streamed content, terminal `done_reason`, and token
metrics. For incomplete search answers, compare `search.response` with
`search.normalized` and `agent.synthesis.input` before reading `agent.result`.

Planning and synthesis currently use the same configured thinking mode, context
window, response-token limit, and five-minute total timeout. When a planning
trace shows many `thinkingRecords`, zero `answerRecords`, and no completed plan,
rerun with `KAOS_OLLAMA_THINKING=off` while diagnosing. Increasing the response
limit can extend hidden planning rather than produce the small required JSON
plan. A synthesis-only plan can still ignore prompt instructions and invent
facts; the trace distinguishes that behavior but cannot make model prose true.
Evidence requirements that must hold on every run need deterministic planner
enforcement rather than prompt wording alone.

If terminal output contains replacement characters while the corresponding
`ollama.chat.result` value is correct in the UTF-8 debug file, the corruption is
at the console/Gradle output boundary rather than in Ollama or KAOS's retained
Java string.

Debugging observes execution; it does not force search, retry a failure, change
validation, or authorize tools. Storage internals and every local variable are
not traced. Inputs, command boundaries, and shared model/tool paths provide
application-wide coverage without a separate logging framework.

## Content and limits

Debug data can include goals, conversation messages, local-file content,
knowledge chunks, model outputs, and public search data. Enable it only when
you intend to inspect those contents, and review logs before sharing them.
Dedicated hidden-reasoning fields, credential fields (such as `password`,
`api_key`, `authorization`, and access tokens), cookies, and grant fields are
excluded recursively from structured data. Raw approval input and executable
grants are never logged. This is not a general secret detector: secrets pasted
inside a prompt or document can still appear as content.

Events contain at most a 32,768-character data preview; larger events are marked
`truncated` with their original serialized length. A run stops tracing after
roughly eight million serialized characters and emits `debug.limit_reached`.
These limits do not change application response limits. Malformed JSON bodies
produce an `invalidJson` marker and size rather than an unfiltered dump. Text is
JSON escaped so embedded newlines/control characters cannot forge trace lines.
Disabled tracing does not evaluate data suppliers. Each invocation owns its
scope; traces do not persist into a later command or background thread.
