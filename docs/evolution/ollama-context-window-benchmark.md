# Ollama context-window benchmark

## Decision

Task [#1067](https://github.com/karanbabu2110/KAOS/issues/1067) selects
**4,096 tokens** as the ordinary KAOS context-window default.

- Use 2,048 only for deliberately short smoke tests where minimum latency is
  more important than retaining longer input.
- Use 4,096 for current ordinary prompt submission.
- Increase the value only when a measured conversation, RAG, coding, or agent
  workload needs more retained input and the hardware cost is acceptable.

The selection is evidence for this development machine and current application,
not a universal model-performance claim.

## Environment

Measured on 2026-08-28:

| Component | Measured value |
| --- | --- |
| Operating environment | Windows / PowerShell |
| Ollama | 0.32.1; fixed local API on `127.0.0.1:11434` |
| Model | `qwen3:8b` |
| GPU | NVIDIA GeForce RTX 2060; 6,144 MiB VRAM; driver 591.86 |
| CPU | AMD Ryzen 5 5600G; 6 cores / 12 logical processors |
| RAM | 31.8 GiB |

## Controlled short-prompt process

The comparison changed only `options.num_ctx`. Every request used:

- model `qwen3:8b`;
- prompt `In exactly two concise sentences, explain why the sky appears blue.`;
- `stream: false`;
- `think: false`;
- `options.num_predict: 64`;
- `options.seed: 42`;
- `options.temperature: 0`;
- the same local endpoint and five-minute client timeout.

For each context size:

1. Run `ollama stop qwen3:8b` and wait two seconds.
2. Submit one cold request and record Ollama's terminal metrics.
3. Submit the identical request immediately as a warm request.
4. Run `ollama ps` to record allocated context and CPU/GPU split.
5. Run `nvidia-smi` to record GPU memory after the warm request.

The request shape was:

```json
{
  "model": "qwen3:8b",
  "prompt": "In exactly two concise sentences, explain why the sky appears blue.",
  "stream": false,
  "think": false,
  "keep_alive": "10m",
  "options": {
    "num_ctx": 4096,
    "num_predict": 64,
    "seed": 42,
    "temperature": 0
  }
}
```

The benchmark repeated that payload with `num_ctx` set to 2,048, 4,096,
8,192, and 16,384. Tokens per second is `eval_count` divided by
`eval_duration` in seconds. Wall time was measured around the local HTTP call.

The following PowerShell reproduces the controlled loop and prints one JSON
summary plus the processor/GPU snapshots for each range:

```powershell
$model = "qwen3:8b"
$prompt = "In exactly two concise sentences, explain why the sky appears blue."

foreach ($context in 2048, 4096, 8192, 16384) {
    ollama stop $model
    Start-Sleep -Seconds 2

    foreach ($phase in "cold", "warm") {
        $payload = @{
            model = $model
            prompt = $prompt
            stream = $false
            think = $false
            keep_alive = "10m"
            options = @{
                num_ctx = $context
                num_predict = 64
                seed = 42
                temperature = 0
            }
        } | ConvertTo-Json -Depth 4 -Compress

        $wall = [Diagnostics.Stopwatch]::StartNew()
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri "http://127.0.0.1:11434/api/generate" `
            -ContentType "application/json" `
            -Body $payload `
            -TimeoutSec 300
        $wall.Stop()

        [ordered]@{
            context = $context
            phase = $phase
            wall_seconds = [math]::Round($wall.Elapsed.TotalSeconds, 2)
            total_seconds = [math]::Round($response.total_duration / 1e9, 2)
            load_seconds = [math]::Round($response.load_duration / 1e9, 2)
            prompt_tokens = $response.prompt_eval_count
            prompt_seconds = [math]::Round($response.prompt_eval_duration / 1e9, 2)
            output_tokens = $response.eval_count
            generation_seconds = [math]::Round($response.eval_duration / 1e9, 2)
            tokens_per_second = [math]::Round(
                $response.eval_count / ($response.eval_duration / 1e9), 2)
            done_reason = $response.done_reason
        } | ConvertTo-Json -Compress
    }

    ollama ps
    nvidia-smi `
        --query-gpu=name,memory.total,memory.used,memory.free,utilization.gpu `
        --format=csv,noheader,nounits
}
```

## Short-prompt results

All eight requests processed 29 prompt tokens, generated the same 48 output
tokens, and stopped normally.

| Context | Phase | Wall | Load | Prompt eval | Generation | Output rate | Loaded size | CPU/GPU | VRAM used |
| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| 2,048 | Cold | 7.86 s | 4.49 s | 0.46 s | 2.81 s | 17.06 tok/s | 5.7 GB | 25% / 75% | 5,159 MiB |
| 2,048 | Warm | 3.24 s | 0.18 s | 0.08 s | 2.97 s | 16.16 tok/s | 5.7 GB | 25% / 75% | 5,159 MiB |
| 4,096 | Cold | 8.15 s | 4.41 s | 0.50 s | 3.23 s | 14.88 tok/s | 6.0 GB | 30% / 70% | 5,139 MiB |
| 4,096 | Warm | 3.79 s | 0.18 s | 0.09 s | 3.51 s | 13.67 tok/s | 6.0 GB | 30% / 70% | 5,139 MiB |
| 8,192 | Cold | 9.74 s | 4.95 s | 0.66 s | 4.12 s | 11.65 tok/s | 6.6 GB | 36% / 64% | 5,119 MiB |
| 8,192 | Warm | 4.87 s | 0.18 s | 0.11 s | 4.58 s | 10.49 tok/s | 6.6 GB | 36% / 64% | 5,119 MiB |
| 16,384 | Cold | 10.83 s | 5.18 s | 0.73 s | 4.91 s | 9.77 tok/s | 7.8 GB | 46% / 54% | 5,171 MiB |
| 16,384 | Warm | 5.38 s | 0.18 s | 0.13 s | 5.06 s | 9.48 tok/s | 7.8 GB | 46% / 54% | 5,171 MiB |

The model did not fit wholly in the 6 GiB GPU at any tested range. Increasing
context increased the loaded allocation and CPU share, and reduced warm output
rate from 16.16 tok/s at 2K to 9.48 tok/s at 16K. The VRAM readings remained
near 5.1 GiB because Ollama shifted more work to CPU as context grew.

## Long-input boundary check

A separate synthetic prompt placed the harmless marker
`KAOS-CONTEXT-MARKER-7429` at the beginning, followed it with repeated neutral
filler, and asked for the marker at the end. It contained 28,907 characters and
was intentionally larger than the current 4,096-character KAOS CLI prompt
limit. It exists only to expose the context-retention boundary relevant to
future conversation or RAG work.

| Context | Prompt tokens processed | Marker retained | Result | Total time |
| ---: | ---: | --- | --- | ---: |
| 2,048 | 1,026 | No | Returned filler text after earlier context was discarded | 6.63 s |
| 4,096 | 3,883 | Yes | Returned `KAOS-CONTEXT-MARKER-7429` exactly | 10.29 s |

This does not prove that 4K is sufficient for future RAG or agents. It proves
that the faster 2K option can lose earlier input in a workload that 4K retains.

## End-to-end KAOS observation

After applying the 4,096-token default, the ordinary KAOS command completed the
same developer question that previously took 2 minutes 43 seconds:

```powershell
$env:KAOS_OLLAMA_MODEL = "qwen3:8b"
./gradlew.bat --% run --args="ollama-prompt \"Why is the sky blue?\""
```

The Gradle run completed successfully in 1 minute 31 seconds. Immediately after
the run, `ollama ps` reported a 4,096-token allocation, 6.0 GB loaded size, and
30% CPU / 70% GPU execution. `nvidia-smi` reported 5,136 MiB used and 810 MiB
free on the 6,144 MiB RTX 2060.

This is a 72-second, approximately 44% elapsed-time reduction from the earlier
end-to-end observation. It is useful operational evidence, but it is not an
isolated context benchmark: Gradle startup is included, thinking was still
implicit, and no provider output-token limit was configured. The verbose answer
and remaining 1 minute 31 second duration support the separate thinking and
response-limit Tasks #1069 and #1070 rather than changing this context decision.

## Interpretation and limitations

- 2K is the fastest measured short-request option, but it has less retention
  margin and is therefore an explicit smoke-test choice rather than the default.
- 4K adds about 0.55 seconds to the measured warm short response while retaining
  the synthetic long-input marker that 2K lost.
- 8K and 16K provide more potential input space but increased CPU offloading and
  reduced generation throughput without helping the controlled short prompt.
- Results are one cold/warm pair per context, not a statistically significant
  performance study. Background load, Ollama versions, model builds, drivers,
  and hardware can change the numbers.
- Task 002.02.04 evaluates models separately. Task 002.02.05 owns thinking
  policy, Task 002.02.06 owns output limits, and Feature 002.04 owns streaming.

## Implemented configuration contract

KAOS sends the selected context as Ollama `options.num_ctx` on every prompt.
Configuration precedence is:

1. `kaos.ollama.context-window` system property;
2. `KAOS_OLLAMA_CONTEXT_WINDOW` environment variable;
3. the evidence-selected 4,096-token default.

Accepted values are whole numbers from 2,048 through 65,536. The upper bound
permits deliberate future evaluation; it does not claim that the current GPU or
every selected model can use that value efficiently or support it natively.
