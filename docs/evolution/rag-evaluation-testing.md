# RAG Evaluation and Testing

Feature [005.09](https://github.com/karanbabu2110/KAOS/issues/873) adds one
deterministic cross-boundary evaluation for the implemented local knowledge
pipeline.

## Evaluated outcome

`RagPipelineEvaluationTest` passes known UTF-8 documents through exact text
extraction, deterministic chunking, version-1 SQLite storage, compatible-vector
retrieval, bounded grounded prompt construction, and source attribution. Fixed
two-dimensional vectors make the expected ranking repeatable without Ollama,
network access, timing assumptions, or model variability.

The successful scenario verifies that:

- the expected source ranks first after a store reopen;
- prompt evidence preserves the ranked source content and document identity;
- citation labels and source coordinates align with the included contexts; and
- the prompt requires citation labels for supported claims.

A failure-boundary scenario proves that vectors stored under another embedding
model do not enter retrieval or the grounded prompt.

## Evaluation limits and findings

This is pipeline correctness evidence, not a semantic quality benchmark. The
current retriever has no relevance threshold, so compatible low-scoring chunks
can remain in the bounded top-three results. The fixed vectors do not measure
embedding quality, recall, precision, latency, or generated-answer faithfulness.

Feature 005.10 now submits the grounded prompt and streams an answer. The fixed
vector evaluation remains deterministic pipeline evidence; it does not evaluate
the generated answer for correctness, unsupported claims, or citation use.

## Verification

```powershell
./gradlew.bat test --tests "io.kaos.knowledge.RagPipelineEvaluationTest" --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```
