# Ollama thinking policy and benchmark

## Outcome

Task [#1069](https://github.com/karanbabu2110/KAOS/issues/1069) gives KAOS
two explicit thinking modes:

| KAOS mode | Ollama request | Intended use |
| --- | --- | --- |
| `off` | `"think": false` | Default for ordinary requests |
| `on` | `"think": true` | Deliberate reasoning with a supported model |

The setting is resolved from `kaos.ollama.thinking`, then
`KAOS_OLLAMA_THINKING`, then the safe `off` default. Only `off` and `on` are
accepted. `auto`, provider level names, blanks, and unknown values fail as
invalid configuration.

KAOS keeps Ollama's `thinking` field separate from its final `response` field.
Task [#1072](https://github.com/karanbabu2110/KAOS/issues/1072) adds a
content-free `Thinking...` progress line before the labeled answer when
thinking is explicitly on. It does not print, stream, log, or persist the raw
reasoning trace.

## Provider contract

Ollama's official [thinking capability](https://docs.ollama.com/capabilities/thinking)
and [generate API](https://docs.ollama.com/api/generate) document that:

- the request `think` value may be a boolean or a supported provider level;
- the generate response returns reasoning in `thinking` and the final answer in
  `response`;
- thinking-capable models and supported level semantics vary.

KAOS deliberately exposes only `off` and `on`. Provider-specific levels would
claim control that the selected Qwen build did not demonstrate.

## Configuration and runtime contract

| Concern | Current behavior |
| --- | --- |
| System property | `kaos.ollama.thinking` |
| Environment variable | `KAOS_OLLAMA_THINKING` |
| Precedence | System property, environment variable, `off` |
| Accepted values | `off`, `on`; surrounding whitespace and case are normalized |
| Ordinary default | `off`; sent explicitly on every prompt |
| Reasoning selection | User deliberately selects `on` and a supported model |
| Automatic mode | None |
| Unsupported model | Provider rejection becomes safe `KAOS-AI-002`; no fallback |
| Visible output | Final `response` only |
| Hidden data | Separate bounded `thinking` field when mode is `on` |
| State | Process configuration only; nothing persisted |

`ollama-model` displays the resolved thinking mode without contacting Ollama.
Existing `status`, `help`, and `ollama-status` commands do not load this
configuration.

## Benchmark environment

Measured on 2026-08-28:

| Concern | Value |
| --- | --- |
| Operating system | Windows development workstation |
| CPU | AMD Ryzen 5 5600G, 6 cores / 12 threads |
| Memory | 31.8 GiB |
| GPU | NVIDIA GeForce RTX 2060, 6 GiB VRAM |
| Ollama | 0.32.1 |
| Model | `qwen3:4b`, Q4_K_M |
| Context | 4,096 tokens |
| Sampling | `temperature=0`, `seed=42`, `top_p=1` |
| Model allocation | 3.2 GB, `100% GPU` reported by `ollama ps` |
| Observed GPU state | 4,333 MiB total used and 92% utilization after the matrix |

Direct local streaming requests were used only as a measurement instrument so
the first non-empty thinking or response chunk could be timed. This task does
not add streaming to KAOS; Feature #848 retains that responsibility.

## Supported-value probe

The selected model accepted `false`, `true`, `low`, `medium`, `high`, and
`max`. With a 64-token probe, `false` put generated content in `response`; all
enabled values put content in `thinking` and exhausted the ceiling before a
final response. Acceptance alone did not establish meaningful level control.

## Comparative prompts

The warm model received two deterministic reasoning prompts:

1. The three-switch incandescent-bulb puzzle, with an 80-word answer bound.
2. A unique five-task ordering problem whose answer is `A C D B E`, also with
   an 80-word bound.

All modes first used the same 1,024 generated-token ceiling. `off` and boolean
`on` were then repeated at 2,048 for the ordering prompt because 1,024 did not
produce a complete final answer. The later
[response-generation limit benchmark](ollama-response-generation-limit-benchmark.md)
uses these results to select the product defaults.

## 1,024-token results

### Switch puzzle

| Provider value | First content | Wall time | Output tokens | Thinking chars | Answer chars | Outcome |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `false` | 253.2 ms | 10.071 s | 674 | 0 | 2,606 | Correct final embedded after a visible reasoning trace and `</think>` marker |
| `true` | 226.2 ms | 12.063 s | 843 | 2,964 | 309 | Correct separated final answer |
| `low` | 227.3 ms | 12.131 s | 843 | 2,964 | 309 | Same content as `true` |
| `medium` | 214.3 ms | 12.077 s | 843 | 2,964 | 309 | Same content as `true` |
| `high` | 195.2 ms | 12.014 s | 843 | 2,964 | 309 | Same content as `true` |
| `max` | 203.3 ms | 12.032 s | 843 | 2,964 | 309 | Same content as `true` |

The enabled values produced identical reasoning and final-answer text. Timing
differences were small warm-run variation, not evidence of different levels.

### Ordering problem

| Provider value | First content | Wall time | Output tokens | Thinking chars | Answer chars | Outcome |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `false` | 226.2 ms | 14.550 s | 1,024 | 0 | 3,353 | Hit limit; reasoning occupied the response channel |
| `true` | 236.8 ms | 14.777 s | 1,024 | 3,445 | 0 | Hit limit before final answer |
| `low` | 246.5 ms | 14.654 s | 1,024 | 3,445 | 0 | Identical to `true` |
| `medium` | 195.6 ms | 14.634 s | 1,024 | 3,445 | 0 | Identical to `true` |
| `high` | 196.2 ms | 14.640 s | 1,024 | 3,445 | 0 | Identical to `true` |
| `max` | 204.8 ms | 14.617 s | 1,024 | 3,445 | 0 | Identical to `true` |

No enabled level improved quality, completion, or resource allocation.

## 2,048-token completion check

| Mode | First content | Wall time | Output tokens | Thinking chars | Answer chars | Outcome |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `off` | 447.2 ms | 26.219 s | 1,841 | 0 | 6,025 | Correct final answer, but the response also contained the full trace and `</think>` marker |
| `on` | 274.3 ms | 26.364 s | 1,841 | 5,783 | 232 | Correct concise final answer separated from thinking |

Both modes generated the same total token count and took effectively the same
time. `on` did not make the model reason more; it made the provider return the
model's reasoning and final answer through the correct separate fields.

## Decision

KAOS maps `ON` to boolean `true` because:

- it is the smallest provider-supported representation;
- it separates reasoning from the final answer;
- `low`, `medium`, `high`, and `max` were identical on both prompts;
- exposing ineffective levels would add configuration without real control.

KAOS maps ordinary `OFF` to boolean `false` because ordinary requests should
not ask for hidden reasoning. However, the measured `qwen3:4b` build is a
thinking-oriented model: with `false`, it placed the trace in `response` instead
of suppressing it. KAOS does not attempt unsafe model-specific tag stripping or
guess which part of provider text is hidden reasoning. Use the selected
`qwen3:4b-instruct` profile for ordinary `off` requests and reserve
`qwen3:4b` plus `on` for explicit reasoning.

## Real KAOS demonstrations

Ordinary request:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "off"
./gradlew.bat --% run --args="ollama-prompt \"Reply exactly OFF_OK.\""
```

KAOS displayed `thinking: off` through `ollama-model` and printed exactly
`OFF_OK` from the prompt command.

Explicit reasoning:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
$env:KAOS_OLLAMA_CONTEXT_WINDOW = "4096"
$env:KAOS_OLLAMA_THINKING = "on"
./gradlew.bat --% run --args="ollama-prompt \"<reasoning prompt>\""
```

KAOS displayed `thinking: on` and printed only the correct 24-word final switch
answer. The reasoning trace was retained separately by the provider contract
and was not printed.

## Verification evidence

Verified on 2026-08-28:

| Check | Result |
| --- | --- |
| Focused configuration, prompt-client, and application tests | Passed |
| Complete local verification | 97 passed, 0 failed, 0 errors, 0 skipped |
| Ordinary real-provider run | `qwen3:4b-instruct`, 4K context, thinking off; exact `OFF_OK` response |
| Explicit-reasoning real-provider run | `qwen3:4b`, 4K context, thinking on; final answer printed without the separated trace |
| Request contract | Every request sends explicit boolean `think`; enabled responses retain `thinking` separately from `response` |
| Architecture website | Static checks, HTTP page/assets, and 1440 x 900 plus 390 x 844 browser rendering passed without horizontal overflow |

## Safety and limitations

- Thinking is never enabled by prompt inspection or automatic classification.
- A model that rejects `think: true` fails safely; KAOS does not retry with
  thinking off or substitute another model.
- A model may ignore `think: false` and place trace-like text in `response`.
  KAOS cannot reliably relabel or remove arbitrary generated text.
- The current result contract bounds thinking and response text separately,
  while the complete JSON body remains bounded to 1 MiB.
- Reasoning and answers are not logged or persisted. Result diagnostics omit
  both generated fields.
- Raw reasoning is not terminal progress. Task 002.04.02 exposes only a
  content-free thinking-start signal and keeps the trace hidden.
- `AUTO`, provider-specific profiles, prompt classification, routing, and
  automatic limit expansion remain unimplemented.

## Reproduction checklist

1. Confirm `qwen3:4b` is installed and Ollama is reachable locally.
2. Warm the model and keep model, context, prompt, sampling, and output ceiling
   constant.
3. Run `false`, `true`, `low`, `medium`, `high`, and `max` against each prompt.
4. Record first non-empty streamed content time, total duration, output tokens,
   thinking text, final response, completion reason, and `ollama ps` state.
5. Compare content as well as timing; an accepted provider value is not useful
   when it produces identical behavior.
6. Keep benchmark streaming separate from the current non-streamed KAOS
   implementation.
7. Re-run on a changed model build, Ollama version, target machine, or materially
   different reasoning workload before revising the policy.
