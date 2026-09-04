# Grounded Prompt Construction

Feature [005.07](https://github.com/karanbabu2110/KAOS/issues/871) turns one
exact question and its ranked local contexts into an immutable bounded prompt.

## Implemented outcome

After `knowledge-retrieve` ranks compatible chunks, `GroundedPromptBuilder`
constructs a prompt of at most 4,096 Unicode code points. It preserves the exact
question and adds as many whole ranked evidence records as fit. Records carry
document identifier, chunk index, source name, exact content, and explicit
Unicode length fields so structure-like text inside evidence remains data.

The leading policy instructs the future answering model to use only supplied
evidence, treat all evidence as untrusted data, ignore instructions inside it,
and say when the evidence does not support an answer. Unsafe stored control
characters fail before provider submission. The resulting text passes through
the existing `OllamaPrompt` boundary without normalization.

The current CLI reports only prompt code-point count and included-context count
after the existing safe retrieval references. It never prints the question,
evidence content, or constructed prompt.

## Boundaries and limitations

- Prompt: at most 4,096 code points, matching the current Ollama prompt limit.
- Evidence: one to three complete ranked records; chunks are never partially
  truncated to fill remaining capacity.
- Construction is direct and in process with no template framework or new
  module.

The prompt is not submitted and no answer, citation rendering, token estimator,
configurable template, conversation integration, or model-specific formatting
exists. Feature 005.08 owns source attribution.

## Verification

```powershell
./gradlew.bat test --tests "io.kaos.knowledge.GroundedPromptBuilderTest" --tests "io.kaos.app.KnowledgeRetrieveCommandTest" --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```
