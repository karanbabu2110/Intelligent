# Ollama response streaming

## Task 002.04.01 outcome

Task [#1071](https://github.com/karanbabu2110/KAOS/issues/1071) changes the
existing local Ollama prompt flow from one buffered JSON response to incremental
newline-delimited JSON. KAOS validates and prints each answer chunk once, in
provider order, while assembling the same chunks into one bounded final answer.
This is the first part of Response Streaming Feature
[#848](https://github.com/karanbabu2110/KAOS/issues/848).

## Implemented flow

```text
terminal
  -> KaosApplication ollama-prompt command
  -> OllamaPromptClient POST /api/generate with stream: true
  <- UTF-8 NDJSON records from fixed loopback Ollama
  -> validate each record and answer chunk
  -> print and flush each answer chunk exactly once
  -> assemble the same chunks into one bounded final answer
  -> validate terminal reason and completion metrics
```

`OllamaPromptClient` continues to use the JDK HTTP client and Jackson already in
the application. No reactive framework, module, event bus, worker, service, or
additional production dependency is introduced.

## Current contract

| Concern | Task 002.04.01 behavior |
| --- | --- |
| Provider | Fixed `http://127.0.0.1:11434/api/generate`; redirects disabled |
| Request | JSON with `stream: true`, explicit model, prompt, thinking boolean, context, and response-token limit |
| Response | `application/x-ndjson` or Ollama-compatible `application/json`; parsed one UTF-8 record at a time |
| Answer | Every non-empty validated answer chunk is printed, flushed, and appended once in order |
| Completion | Requires `done: true`, `done_reason: stop`, and non-negative total duration, prompt-token, generated-token, and generation-duration metrics |
| Bounds retained | At most 1 MiB streamed provider data and 65,536 Unicode code points each for assembled answer and separated thinking |
| Thinking | Kept separate for compatibility when enabled, but never sent to the answer-output callback or terminal |
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

## Privacy, limitations, and next checkpoints

Prompts and responses remain in process and are sent only to local loopback
Ollama. KAOS does not persist them or include prompts, raw response records,
thinking text, or exception details in failure diagnostics.

Task #1071 deliberately does not present thinking progress or raw reasoning;
that remains Task [#1072](https://github.com/karanbabu2110/KAOS/issues/1072).
It also does not complete cancellation, partial-output labeling, inactivity
bounds, or the complete streaming boundary policy; those remain Task
[#1073](https://github.com/karanbabu2110/KAOS/issues/1073). Feature #848 remains
in progress until both later tasks and the feature-level verification pass.
