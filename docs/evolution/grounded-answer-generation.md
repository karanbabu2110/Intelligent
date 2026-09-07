# Grounded Answer Generation

Feature [005.10](https://github.com/karanbabu2110/KAOS/issues/1091) completes the
missing Epic 005 user path: ask one question and receive an answer generated
from retrieved local context.

## Implemented outcome

`knowledge-ask <question>` reuses the existing foreground pipeline. It validates
and embeds the exact question, loads the local SQLite knowledge snapshot, ranks
compatible chunks, constructs the bounded injection-aware prompt, and submits
that exact prompt through the configured loopback Ollama streaming client.

Validated answer chunks are displayed once and in order. Only after a clean
terminal provider response does KAOS print the citation manifest for the exact
evidence records included in the prompt. The manifest contains prompt-local
labels, stored-document identifiers, safe source names, and chunk indexes.

## Failure and privacy behavior

- Invalid questions, embedding failures, unsafe prompt input, unavailable or
  incompatible stored context, and storage failures use the existing classified
  knowledge errors.
- Model configuration, provider, response-limit, timeout, interruption, and
  stream failures use the existing AI errors.
- If validated answer text was already displayed before failure, the existing
  partial-output warning remains visible and no citation manifest is printed.
- Errors do not echo the question, evidence, prompt, model names, vectors, or
  provider body.

The generated answer may quote or summarize private source content. Asking a
question displays that model output on the local terminal; the manifest is a
list of supplied sources, not proof that the model used them correctly.

## Boundaries and limitations

- Both model calls remain fixed to loopback Ollama and require explicit chat and
  embedding model configuration.
- Retrieval still returns up to three compatible chunks without a relevance
  threshold or reranking.
- Citation labels tell the model how to attribute evidence and expose the source
  records it received. KAOS does not yet verify generated claims or reject an
  answer that omits, invents, or misuses a citation.
- Knowledge answers are not added to conversation history or persisted.

No new module, service, database, background work, remote provider, or general
RAG framework is introduced.

## Verification

```powershell
./gradlew.bat test --tests "io.kaos.app.KnowledgeAskCommandTest" --tests "io.kaos.app.KnowledgeRetrieveCommandTest" --tests "io.kaos.app.CommandRouterTest" --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Validation: 254 passed, one existing skip, zero failures or errors; all 11
verifyLocal tasks passed. The HTTP integration test uses a real Ollama client
against a local stub and a temporary SQLite database, asserting exact prompt
transfer and ordered output. Installed-model answer quality has not been
evaluated in the feature checkpoint. The later
[Epic 005 exit](epic-005-exit.md) records the successful installed-model
demonstration and the bounded epic conclusion.
