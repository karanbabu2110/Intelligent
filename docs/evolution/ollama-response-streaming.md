# Ollama response streaming

## Tasks 002.04.01 through 002.04.03 outcome

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

Task [#1073](https://github.com/karanbabu2110/KAOS/issues/1073) completes the
stream lifecycle. KAOS requests one JDK publisher item at a time, enforces total
and inactivity deadlines, cancels the subscription on interruption or a hard
local bound, and clearly labels visible output when completion is not clean.

## Implemented flow

```text
terminal
  -> KaosApplication ollama-prompt command
  -> OllamaPromptClient POST /api/generate with stream: true
  <- one backpressured publisher item at a time from fixed loopback Ollama
  -> enforce byte, total-duration, inactivity, and interruption boundaries
  -> reconstruct UTF-8 and parse NDJSON records
  -> validate each record and the thinking-to-answer lifecycle
  -> optionally signal Thinking... without reasoning text
  -> transition to Answer: for explicit thinking mode
  -> print and flush each answer chunk exactly once
  -> assemble the same chunks into one bounded final answer
  -> validate terminal reason and completion metrics
  -> on non-clean completion, cancel resources and label visible output partial
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
| Transport bounds | At most 1 MiB provider data, five minutes overall, 60 seconds without publisher data, and one requested publisher item at a time |
| Text bounds | At most 65,536 Unicode code points each for assembled answer and separated thinking |
| Thinking off | Sends `think: false`; any unexpected provider thinking record is invalid; no progress label is rendered |
| Thinking on | Sends `think: true`; the first non-empty validated thinking record emits one content-free progress signal; raw trace stays separately bounded and hidden |
| Transition | Thinking may precede answer content; thinking arriving after answer output starts is invalid; the answer heading is emitted once |
| Cancellation | Command-thread interruption cancels the HTTP subscription, closes the response, preserves the interrupt flag, and stops further output; Ctrl+C reaches this path through a command-scoped shutdown hook |
| Failure classes | Clean stop, provider length completion, local safety limit, timeout, interruption, malformed/incomplete data, rejection, and transport failure remain distinguishable without raw provider or prompt data |
| Partial output | A handled failure after visible content first terminates the stdout line, then identifies that output as partial in safe stderr guidance; no retry or resume occurs |

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
- missing metrics, unknown completion, provider length completion, unsafe
  content, independent byte/answer/thinking ceilings, response rejection,
  content type, unavailability, and the loopback-only endpoint boundary;
- cancellation after a visible chunk with interrupt preservation, plus separate
  total and no-data deadlines against a deliberately gated loopback stream;
- application output wiring without duplicate final-answer printing, including
  exact partial-output labeling for truncation, cancellation, malformed data,
  and local safety limits.

Focused command and current result:

```powershell
./gradlew.bat test --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --tests 'io.kaos.app.KaosApplicationTest' --no-daemon --warning-mode=all
```

The focused command passed. The complete from-clean-state checkpoint passed all
11 `verifyLocal` tasks with 107 tests, zero failures, errors, or skipped tests.

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

The Task 002.04.03 real-provider check reran both modes through the
publisher-backed transport. `qwen3:4b-instruct`, thinking off, and a 64-token
limit produced exact `BOUND_OK`: first content at 4,355 ms, completion at
4,874 ms, exit `0`, and empty stderr. `qwen3:4b`, thinking on, and a 2,048-token
limit produced exact `Thinking...`, `Answer:`, and `15` lines: first progress at
3,969 ms, completion at 9,601 ms, exit `0`, and empty stderr. A controlled
Ctrl+C probe used `qwen3:4b`, thinking on, a 4,096-token limit, and a deliberately
long prompt. After `Thinking...` became visible, Ctrl+C returned within 0.4
seconds with exit `1`, no further generated content, and the exact safe partial
cancellation diagnostic. A warm 512-token follow-up also completed with exact
`Thinking...`, `Answer:`, and `42`, illustrating that a provider token ceiling
is a maximum rather than a deterministic truncation trigger.

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

## Task 002.04.03 cancellation and boundary policy

The ordinary success path remains unchanged. Non-clean endings are classified
before terminal guidance:

| Ending | Result |
| --- | --- |
| `done_reason: stop` with required metrics | Clean success; assembled answer retained |
| `done_reason: length` | Provider generation boundary; `KAOS-AI-003` |
| More than 1 MiB, 65,536 answer code points, or 65,536 thinking code points | Local safety boundary; `KAOS-AI-003` |
| Five-minute total deadline or 60 seconds without data | Timed out; `KAOS-AI-002` |
| Command-thread interruption or Ctrl+C | Subscription cancelled; `KAOS-AI-002` when the process remains available to report it |
| Malformed, incomplete, unsafe, or metric-free stream | Invalid response; `KAOS-AI-002` |

When any handled non-clean ending follows visible output, KAOS writes:

```text
ERROR [<safe code>] Partial streaming output was displayed before clean completion. <recovery>
```

The error is on stderr. If the answer did not end with a line break, KAOS first
adds one on stdout so the diagnostic cannot be appended to generated content.
The response subscription is cancelled when reading stops, including hard
limits and interruption. There is no automatic retry because replaying a prompt
after partially visible output could duplicate or contradict content.

## Privacy, limitations, and next checkpoints

Prompts and responses remain in process and are sent only to local loopback
Ollama. KAOS does not persist them or include prompts, raw response records,
thinking text, or exception details in failure diagnostics.

The Ctrl+C shutdown hook exists only for the lifetime of the foreground command
and waits at most two seconds for cleanup. It does not stop or own Ollama. The
CLI still accepts one prompt and has no conversation, retry, resume, persistence,
remote-provider, or concurrent-request behavior. Broader reusable error and
timeout taxonomy remains Feature
[#849](https://github.com/karanbabu2110/KAOS/issues/849). Feature #848 remains in
progress through its complete review and pull request.
