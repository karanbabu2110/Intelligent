# Ollama response streaming

## Tasks 002.04.01 and 002.04.02 outcome

Task [#1071](https://github.com/karanbabu2110/KAOS/issues/1071) changes the
existing local Ollama prompt flow from one buffered JSON response to incremental
newline-delimited JSON. KAOS validates and prints each answer chunk once, in
provider order, while assembling the same chunks into one bounded final answer.
This is the first part of Response Streaming Feature
[#848](https://github.com/karanbabu2110/KAOS/issues/848).

Task [#1072](https://github.com/karanbabu2110/KAOS/issues/1072) adds a safe
visible lifecycle for explicitly enabled thinking. KAOS emits one content-free
`Thinking...` line when the first validated thinking record arrives, then one
`Answer:` heading before streaming answer chunks. Raw reasoning is never passed
to the terminal renderer.

## Implemented flow

```text
terminal
  -> KaosApplication ollama-prompt command
  -> OllamaPromptClient POST /api/generate with stream: true
  <- UTF-8 NDJSON records from fixed loopback Ollama
  -> validate each record and the thinking-to-answer lifecycle
  -> optionally signal Thinking... without reasoning text
  -> transition to Answer: for explicit thinking mode
  -> print and flush each answer chunk exactly once
  -> assemble the same chunks into one bounded final answer
  -> validate terminal reason and completion metrics
```

`OllamaPromptClient` continues to use the JDK HTTP client and Jackson already in
the application. No reactive framework, module, event bus, worker, service, or
additional production dependency is introduced.

## Current contract

| Concern | Implemented Feature 002.04 behavior |
| --- | --- |
| Provider | Fixed `http://127.0.0.1:11434/api/generate`; redirects disabled |
| Request | JSON with `stream: true`, explicit model, prompt, thinking boolean, context, and response-token limit |
| Response | `application/x-ndjson` or Ollama-compatible `application/json`; parsed one UTF-8 record at a time |
| Answer | Every non-empty validated answer chunk is printed, flushed, and appended once in order |
| Completion | Requires `done: true`, `done_reason: stop`, and non-negative total duration, prompt-token, generated-token, and generation-duration metrics |
| Bounds retained | At most 1 MiB streamed provider data and 65,536 Unicode code points each for assembled answer and separated thinking |
| Thinking off | Sends `think: false`; any unexpected provider thinking record is invalid; no progress label is rendered |
| Thinking on | Sends `think: true`; the first non-empty validated thinking record emits one content-free progress signal; raw trace stays separately bounded and hidden |
| Transition | Thinking may precede answer content; thinking arriving after answer output starts is invalid; the answer heading is emitted once |
| Failure | Malformed, incomplete, unsafe, oversized, unsupported, or metric-free streams return a safe existing status without raw provider or prompt data |

Java's UTF-8 `InputStreamReader` retains partial byte sequences across network
reads, so a multi-byte character split across writes is reconstructed before
JSON parsing and output validation.

## Deterministic evidence

The focused loopback tests cover:

- ordered multi-record answers and exact final assembly;
- observing the first chunk while the test server deliberately withholds the
  terminal record;
- a Unicode character split inside its UTF-8 byte sequence across three writes;
- thinking separation without answer-output contamination;
- one thinking-start signal across multiple thinking chunks, the visible
  thinking-to-answer transition, ordinary off behavior, direct answers with no
  false progress claim, and rejection of thinking after answer output starts;
- malformed and incomplete streams;
- missing metrics, unknown completion, provider length completion, unsafe and
  oversized output, response rejection, content type, timeout, interruption,
  unavailability, and the loopback-only endpoint boundary;
- application output wiring without duplicate final-answer printing.

Focused command and current result:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
```

The focused command passed. The complete from-clean-state checkpoint also
passed all 11 `verifyLocal` tasks.

The real local demonstration used Ollama with installed `qwen3:1.7b`, thinking
off, a 64-token response limit, and the prompt `Reply exactly STREAM_OK.`. KAOS
made the first answer character visible after 3,811 ms, completed after 4,310 ms,
printed exactly `STREAM_OK`, returned exit code `0`, and wrote nothing to
standard error. These timings are one local observation, not a service-level
objective or performance guarantee.

The Task 002.04.02 comparison used the installed ordinary and reasoning models.
With `qwen3:4b-instruct`, thinking off, a 64-token response limit, and the prompt
`Reply exactly OFF_OK.`, KAOS made the first answer character visible after
7,058 ms and completed after 7,518 ms with exact `OFF_OK` output. With
`qwen3:4b`, thinking on, the documented 2,048-token reasoning limit, and the
prompt `What is 17 + 25? Answer with only the number.`, KAOS displayed
`Thinking...` after 1,038 ms, completed after 6,551 ms, and printed the exact
`Thinking...`, `Answer:`, and `42` lines. Both successful runs returned exit
code `0` with empty standard error. The on-mode progress label was therefore
visible 5,513 ms before its completed answer. These measurements compare the
visible behavior, not model performance: the models and limits differ by the
documented policy. A separate 512-token on-mode probe displayed `Thinking...`
after 1,290 ms, then safely returned `KAOS-AI-003` without an answer when the
reasoning consumed the smaller boundary.

## Task 002.04.02 display policy

Ordinary `off` mode preserves the Task 002.04.01 output contract: only answer
chunks are printed. Explicit `on` mode uses these exact content-free labels:

```text
Thinking...
Answer:
<streamed answer content>
```

`Thinking...` is printed only when the provider actually emits a non-empty,
validated thinking chunk. If the provider accepts thinking mode but emits an
answer directly, KAOS starts at `Answer:` rather than claiming hidden work. A
provider rejection remains the existing safe prompt error; KAOS does not retry
with thinking off or select another model.

## Privacy, limitations, and next checkpoints

Prompts and responses remain in process and are sent only to local loopback
Ollama. KAOS does not persist them or include prompts, raw response records,
thinking text, or exception details in failure diagnostics.

Tasks #1071 and #1072 do not complete cancellation, partial-output labeling,
inactivity bounds, or the complete streaming boundary policy; those remain Task
[#1073](https://github.com/karanbabu2110/KAOS/issues/1073). Feature #848 remains
in progress until that final task and the feature-level verification pass.
