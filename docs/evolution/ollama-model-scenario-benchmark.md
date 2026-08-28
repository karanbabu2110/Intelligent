# Ollama model scenario benchmark

## Outcome

Task [#1068](https://github.com/karanbabu2110/KAOS/issues/1068)
compared a deliberately small local-model set on the current KAOS development
machine. The result is guidance for explicit local selection, not an application
default:

| Scenario | Recommended model | Reason |
| --- | --- | --- |
| Connectivity smoke test | `qwen3:1.7b` | Smallest allocation and fastest warm generation; use only to prove the request path |
| Ordinary local use | `qwen3:4b-instruct` | Best instruction following across answers, summaries, and Java help while remaining fully on the GPU |
| Explicit reasoning | `qwen3:4b` | Produces a correct final answer when thinking is intentionally allowed enough budget; too slow and verbose for ordinary use |

KAOS continues to require `KAOS_OLLAMA_MODEL` or the corresponding system
property. It does not download, persist, silently replace, or automatically
route between these models.

## Scope and decision rules

The comparison answered one practical question: which small installed model
should a solo developer select for each current scenario? It did not create a
benchmark framework, model registry, routing service, fallback chain, or
provider abstraction.

The candidates were:

| Model | Ollama artifact | Installed allocation | License reported by `ollama show` | Role in comparison |
| --- | --- | --- | --- | --- |
| [`qwen3:1.7b`](https://ollama.com/library/qwen3:1.7b) | 2.0B parameters, Q4_K_M | 1.7 GB | Apache 2.0 | Small smoke-test candidate |
| [`qwen3:4b-instruct`](https://ollama.com/library/qwen3:4b-instruct) | 4.0B parameters, Q4_K_M | 3.2 GB | Apache 2.0 | Direct instruction candidate |
| [`qwen3:4b`](https://ollama.com/library/qwen3:4b) | 4.0B parameters, Q4_K_M | 3.2 GB | Apache 2.0 | Thinking candidate |
| [`gemma3:4b`](https://ollama.com/library/gemma3:4b) | 4.3B parameters, Q4_K_M | 2.9 GB | Gemma Terms of Use | Alternative-family control |

The installed allocations above are the values reported by `ollama ps` at a
4,096-token context. All four models reported `100% GPU` on this machine. They
are local measurements, not portable hardware requirements.

## Test environment

Measured on 2026-08-28:

| Concern | Value |
| --- | --- |
| Operating system | Windows development workstation |
| CPU | AMD Ryzen 5 5600G, 6 cores / 12 threads |
| Memory | 31.8 GiB |
| GPU | NVIDIA GeForce RTX 2060, 6 GiB VRAM |
| Ollama | 0.32.1 |
| Context | 4,096 tokens |
| Ordinary comparison | `think=false`, `temperature=0`, `seed=42`, `top_p=1` |
| Ordinary output ceiling | 128 generated tokens |
| Coding retest ceiling | 256 generated tokens |
| Request mode | Direct local `POST /api/generate`, non-streamed |

The candidates were installed explicitly before measurement:

```powershell
ollama pull qwen3:1.7b
ollama pull qwen3:4b-instruct
ollama pull qwen3:4b
```

`gemma3:4b` was already installed. Installing candidates was a developer action;
KAOS did not initiate it.

## Prompt suite and assessment

Each model received the same prompt for each ordinary scenario:

1. Explain the blue sky to a 12-year-old in exactly three sentences, mentioning
   Rayleigh scattering and the blue-versus-violet distinction.
2. Summarize a fixed KAOS evolutionary-development paragraph in exactly two
   bullets of at most 15 words each.
3. Return Java 21 code only for an O(n) `twoSum` implementation that returns
   distinct indices and throws when no pair exists.
4. Solve the three-switch incandescent-bulb puzzle in no more than 80 words.

Assessment considered factual correctness, explicit instruction following,
whether a usable final answer was produced, and scenario-specific correctness.
The Java responses were inspected for a complete O(n) map-based solution and
the required exception; they were not compiled as production code.

## Performance results

The first ordinary request after unloading each model was recorded as cold and
then immediately repeated warm. The remaining ordinary scenarios were warm.

| Model | Ordinary cold | Ordinary warm | Warm generation rate | `ollama ps` allocation |
| --- | ---: | ---: | ---: | ---: |
| `qwen3:1.7b` | 21.80 s | 1.07 s | 130.07 tok/s | 1.7 GB |
| `qwen3:4b-instruct` | 3.08 s | 1.37 s | 72.12 tok/s | 3.2 GB |
| `qwen3:4b` | 5.47 s | 2.00 s | 71.67 tok/s | 3.2 GB |
| `gemma3:4b` | 8.30 s | 2.32 s | 55.34 tok/s | 2.9 GB |

The 1.7B cold measurement includes an initial model-transition/setup delay and
must not be interpreted as its normal load time. Its immediately repeated warm
result and generation rate are the useful smoke-test evidence.

Warm scenario timings from the 128-token comparison were:

| Model | Summary | Coding | Reasoning |
| --- | ---: | ---: | ---: |
| `qwen3:1.7b` | 0.55 s | 1.21 s, truncated | 0.88 s |
| `qwen3:4b-instruct` | 0.68 s | 2.01 s, truncated | 1.33 s |
| `qwen3:4b` | 2.02 s, no final | 2.04 s, no code | 2.01 s, no final |
| `gemma3:4b` | 2.23 s | 2.89 s, truncated | 2.72 s |

Every coding response reached the common 128-token ceiling, so coding quality
was reassessed with the same 256-token ceiling for all models. Model switching
caused fresh-load wall times in this second pass, so generation rate and outcome
are more comparable than wall time.

| Model | 256-token coding outcome | Generation rate |
| --- | --- | ---: |
| `qwen3:1.7b` | Complete correct code, but added explanation despite `code only`; hit ceiling after the code | 120.10 tok/s |
| `qwen3:4b-instruct` | Complete correct code and stopped naturally at 148 tokens | 68.31 tok/s |
| `qwen3:4b` | Used all 256 tokens describing an approach; emitted no code | 69.30 tok/s |
| `gemma3:4b` | Complete correct code and stopped naturally at 248 tokens | 61.50 tok/s |

## Quality observations

| Scenario | `qwen3:1.7b` | `qwen3:4b-instruct` | `qwen3:4b` | `gemma3:4b` |
| --- | --- | --- | --- | --- |
| Ordinary answer | Missed sentence constraint and misstated the violet comparison | Accurate and exactly three sentences | No final answer inside 128 tokens | Missed constraints and misstated the violet comparison |
| Summary | Correct main idea; exceeded the bullet word limit | Accurate and followed both format limits | No final answer | Added unrequested text and overstated the source |
| Java help | Functionally correct; ignored `code only` | Functionally correct and best instruction following | No code | Functionally correct |
| Light reasoning, thinking off | Incorrect switch method | Correct concise method | No final answer | Correct method |

The 1.7B model is therefore a speed and connectivity tool, not the ordinary
quality baseline. The instruct model was the only candidate that consistently
combined usable answers with the requested format.

## Separate thinking probe

Thinking was evaluated separately because Task #1069 owns the product thinking
policy. The probe used the same switch puzzle and held the 4K context,
temperature, seed, and non-streamed request constant.

| Model | Explicit-thinking result |
| --- | --- |
| `qwen3:1.7b` | Consumed all 512 tokens in thinking and produced no final answer |
| `qwen3:4b-instruct` | Returned the correct 78-token answer with no separate thinking content |
| `qwen3:4b` | No final at 512; at a 1,024 ceiling it stopped after 674 generated tokens in 18.06 s and returned a correct 40-word answer |
| `gemma3:4b` | Ollama rejected `think=true` as unsupported |

This supports `qwen3:4b` only as an opt-in explicit-reasoning profile. It also
shows why Task #1069 must set and verify thinking behavior and why Task #1070
must establish response-generation limits. No thinking or output-limit product
policy is implemented by this benchmark.

## Practical selection

Use the smallest profile that matches the immediate goal:

```powershell
# Fast request-path smoke test; do not use its answer as the quality baseline.
$env:KAOS_OLLAMA_MODEL = "qwen3:1.7b"

# Recommended ordinary local development profile.
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"

# Opt-in reasoning profile; expect greater delay and token use.
$env:KAOS_OLLAMA_MODEL = "qwen3:4b"
```

Keep `KAOS_OLLAMA_CONTEXT_WINDOW=4096` for these current scenarios unless a
measured input requires more. A missing model must fail visibly at Ollama; KAOS
must not silently download the recommendation or substitute another model.

## Decision boundaries

- These are development-machine recommendations, not universal production
  choices.
- The ordinary recommendation can change when KAOS gains larger prompts, RAG,
  tool calling, structured output, or a different target machine.
- `gemma3:4b` remains a useful alternative-family control, but its weaker
  instruction following did not justify choosing it over the instruct model.
- Model routing, discovery, automatic fallback, remote models, and persistent
  profiles remain unimplemented.
- Task #1069 owns ordinary-versus-thinking request behavior. Task #1070 owns
  generated-response limits. Feature #848 owns response streaming.

## Reproduction checklist

1. Record `ollama --version`, CPU, RAM, GPU, and installed model identifiers.
2. Explicitly pull only the candidate models the developer approves.
3. Set 4K context, deterministic sampling values, thinking mode, and a common
   output ceiling in each direct Ollama request.
4. Unload the current candidate before its cold request; immediately repeat the
   same prompt warm.
5. Run the unchanged scenario prompts and retain both response text and Ollama
   duration/token counters.
6. Use `ollama ps` after each candidate to record allocation and processor split.
7. Separate any changed output ceiling or thinking probe from the ordinary
   matrix instead of comparing unlike runs as though they were identical.
8. Re-evaluate before changing a recommendation on different hardware or for a
   materially different KAOS workload.

