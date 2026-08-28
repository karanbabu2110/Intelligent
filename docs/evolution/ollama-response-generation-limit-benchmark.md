# Ollama response-generation limit benchmark

## Outcome

Task [#1070](https://github.com/karanbabu2110/KAOS/issues/1070) gives every
KAOS prompt an explicit bounded Ollama `options.num_predict` value:

| Request mode | Default | Reason |
| --- | ---: | --- |
| Ordinary thinking `off` | 512 tokens | The selected ordinary model completed the representative code request at 163 tokens; 512 leaves practical headroom without forcing unused generation |
| Deliberate thinking `on` | 2,048 tokens | A simple reasoning answer needed 992 tokens and a harder ordering problem needed 1,841 tokens |

Users may override the current default with
`kaos.ollama.response-token-limit` or
`KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT`. Accepted values are 64 through 4,096 whole
tokens. The system property has precedence over the environment variable.

The token limit controls normal provider generation. It does not replace the
independent 1 MiB HTTP body limit, 65,536-code-point answer limit, or
65,536-code-point separated-thinking limit.

## Provider contract

Ollama's official [Modelfile reference](https://docs.ollama.com/modelfile)
defines `num_predict` as the maximum number of tokens to generate and documents
an unbounded `-1` default. KAOS never relies on that unbounded default.

The official [generate API](https://docs.ollama.com/api/generate) returns
`done`, `done_reason`, and `eval_count`. The current Ollama implementation
defines `stop` for natural model completion and `length` for a length boundary.
Local Ollama 0.32.1 returned those values consistently in this benchmark.

KAOS interprets the final provider result as follows:

| Provider result | KAOS result |
| --- | --- |
| `done: true`, `done_reason: stop`, nonblank safe answer | Success; print only the final answer |
| `done: true`, `done_reason: length` | `TOKEN_LIMIT_REACHED`; print no partial answer and report `KAOS-AI-003` |
| Missing, non-text, or unknown reason | Invalid provider response |
| `done` absent, non-boolean, or false | Invalid provider response |

The `length` reason can represent the configured response-token ceiling or a
context-length boundary. Recovery guidance therefore tells the user to review
both limits instead of claiming that one specific boundary was responsible.

## Benchmark environment

Measured on 2026-08-28:

| Concern | Value |
| --- | --- |
| Operating system | Windows development workstation |
| CPU | AMD Ryzen 5 5600G, 6 cores / 12 threads |
| Memory | 31.8 GiB |
| GPU | NVIDIA GeForce RTX 2060, 6 GiB VRAM |
| Ollama | 0.32.1 |
| Context | 4,096 tokens |
| Sampling | `temperature=0`, `seed=42`, `top_p=1` |
| Transport | Direct local non-streamed `POST /api/generate` |

The first request for each model included model loading. Later requests were
warm. Generation rate, completion reason, token count, and content outcome are
more useful than comparing the two cold wall times.

## Exact prompts

Ordinary code request:

```text
Return Java 21 code only for a static int[] twoSum(int[] numbers, int target)
method. It must run in O(n), return distinct indices, and throw
IllegalArgumentException when no pair exists.
```

Reasoning request:

```text
You are outside a closed room with three switches. Exactly one switch controls
an incandescent bulb inside. You may manipulate the switches, then enter the
room only once. Explain how to identify the correct switch in at most 80 words.
```

## Ordinary-model comparison

Model: `qwen3:4b-instruct`, thinking `false`.

| Limit | Done reason | Generated tokens | Answer chars | Wall time | Generation rate | Outcome |
| ---: | --- | ---: | ---: | ---: | ---: | --- |
| 64 | `length` | 64 | 245 | 7.116 s | 71.39 tok/s | Incomplete Java method |
| 256 | `stop` | 163 | 687 | 2.464 s | 72.12 tok/s | Complete code-only answer |
| 512 | `stop` | 163 | 687 | 2.451 s | 72.58 tok/s | Identical natural completion |
| 1,024 | `stop` | 163 | 687 | 2.453 s | 72.17 tok/s | Identical natural completion |

Increasing the maximum did not make Ollama generate more after the model chose
to stop. A 256-token ceiling is the smallest measured value that completed this
request. The 512-token default adds headroom for ordinary explanations and
slightly larger code without granting the 1,024-token allowance by default.

The larger manual 8-Queens prompts recorded in the
[model scenario benchmark](ollama-model-scenario-benchmark.md) show why
generated code still requires compile-and-test validation and why a user may
deliberately select 1,024 or more for a larger request.

## Reasoning-model comparison

Model: `qwen3:4b`, thinking `true`.

| Limit | Done reason | Generated tokens | Thinking chars | Answer chars | Wall time | Generation rate | Outcome |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 64 | `length` | 64 | 282 | 0 | 5.463 s | 71.80 tok/s | No final answer |
| 256 | `length` | 256 | 998 | 0 | 3.760 s | 72.16 tok/s | No final answer |
| 512 | `length` | 512 | 1,972 | 0 | 7.330 s | 72.07 tok/s | No final answer |
| 1,024 | `stop` | 992 | 3,436 | 203 | 13.897 s | 72.52 tok/s | Correct separated final answer, but only 32 tokens of ceiling remained |

The previous controlled ordering prompt provides the necessary harder check:

| Limit | Done reason | Generated tokens | Thinking chars | Answer chars | Outcome |
| ---: | --- | ---: | ---: | ---: | --- |
| 1,024 | `length` | 1,024 | 3,445 | 0 | No final answer |
| 2,048 | `stop` | 1,841 | 5,783 | 232 | Correct separated final answer |

Reasoning consumes the same generated-token budget for thinking and final
answer. A 1,024 default would make the simple result fragile and fail the
harder measured case. The 2,048 default is therefore evidence-based rather than
an assumption that reasoning always needs a fixed multiplier.

### Follow-up field reproduction

An interactive `qwen3:4b` run with thinking enabled reported `KAOS-AI-003` for
`why is the sky blue?` after about nine seconds. The originating PowerShell
session's resolved response limit was not captured, so that result alone cannot
show whether the 2,048 default or a persistent lower test override was used.

The same prompt was then reproduced with
`KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT` explicitly removed. `ollama-model` displayed
the 2,048-token reasoning default, and the real KAOS command completed
successfully in 37 seconds. A matching direct provider request recorded:

| Limit | Done reason | Generated tokens | Thinking chars | Answer chars | Wall time | Outcome |
| ---: | --- | ---: | ---: | ---: | ---: | --- |
| 2,048 | `stop` | 1,314 | 2,559 | 3,401 | 18.763 s | Complete explanation |

This follow-up does not prove every sampled response will fit in 2,048 tokens.
It does show that the reported field failure is not sufficient evidence for
raising the default. Inspect the resolved configuration before diagnosing a
boundary result because PowerShell environment overrides persist for the
current terminal session.

## Configuration contract

```powershell
# Ordinary default: 512
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_THINKING = "off"

# Reasoning default: 2048
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
$env:KAOS_OLLAMA_THINKING = "on"

# Deliberate bounded override for a larger ordinary response
$env:KAOS_OLLAMA_RESPONSE_TOKEN_LIMIT = "1024"
```

`ollama-model` displays the resolved limit without contacting the provider.
Every `ollama-prompt` request sends it as `options.num_predict`.

Values below 64, above 4,096, blank, signed, decimal, non-numeric, or outside
the Java integer range fail as invalid configuration. KAOS does not support
`-1`, `0`, an `unbounded` keyword, or automatic expansion after truncation.

## Safety and limitations

- A length-truncated provider response is intentionally discarded by the
  current terminal application. It returns exit code 1 and `KAOS-AI-003` so
  partial prose or code cannot masquerade as complete.
- KAOS does not retry with a larger limit. The user remains in control of token
  use, latency, and the next request.
- The 64-4,096 range is a current local-development envelope, not a permanent
  platform API promise.
- `num_predict` is a provider token budget. The body and text ceilings remain
  application memory and terminal-safety controls even when the token budget
  is smaller.
- The existing five-minute timeout is unchanged. Timeout classification and
  recovery remain Feature #849.
- Streaming, progressive truncation visibility, and cancellation remain
  Feature #848.
- Prompt tokens and generated tokens share the model context. A larger response
  limit cannot create context capacity.

## Verification evidence

Verified on 2026-08-28:

| Check | Result |
| --- | --- |
| Controlled provider matrix | Eight 64/256/512/1,024 requests completed and recorded with reason, token count, content sizes, time, and generation rate |
| Focused configuration, prompt-client, and application tests | Passed |
| Complete local verification | 97 passed, 0 failed, 0 errors, 0 skipped; all 11 `verifyLocal` tasks executed |
| Ordinary default | Real `ollama-model` run displayed 512 tokens for thinking off |
| Reasoning default | Real `ollama-model` run displayed 2,048 tokens for thinking on |
| Natural completion | Real 512-token `ollama-prompt` printed exact `LIMIT_OK`; exit 0 |
| Length completion | Real 64-token Java request printed no partial answer and returned `KAOS-AI-003`; exit 1 |
| Runtime dependencies | No new dependency; existing Jackson JSON boundary is unchanged |
| Architecture website | Local links and accessibility checks passed; 1,440 x 900 and 390 x 844 browser rendering had no page overflow or console warnings |

## Reproduction checklist

1. Confirm the two selected models and Ollama version.
2. Keep the prompts, 4K context, thinking choice, temperature, seed, and top-p
   constant.
3. Run 64, 256, 512, and 1,024 in ascending order for each model.
4. Record `done_reason`, `eval_count`, thinking and answer characters, wall time,
   and `eval_count / eval_duration`.
5. Repeat a harder reasoning prompt at 1,024 and 2,048 before changing the
   reasoning default.
6. Review generated content manually; a natural stop is not proof of factual or
   code correctness.
7. Re-run after changing the model build, Ollama version, hardware, or target
   workload.
8. Run `ollama-model` immediately before a field reproduction and record the
   displayed context, thinking mode, and response limit.
